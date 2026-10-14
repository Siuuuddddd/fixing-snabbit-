package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.dto.request.WorkerOptionVisitQuery
import com.snabbit.prototype.booking.dto.request.WorkerOptionsQueryRequest
import com.snabbit.prototype.booking.dto.response.WorkerOptionEvaluationResponse
import com.snabbit.prototype.booking.dto.response.WorkerOptionSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionsSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionVisitSnapshot
import com.snabbit.prototype.booking.exception.BookingNotFound
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.matching.MatchingClients
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkerOptionsService(
    private val bookings: BookingRepository,
    private val holds: SlotHoldRepository,
    private val catalog: ServiceCatalogService,
    private val clients: MatchingClients,
    private val completions: BookingCompletionQueryService,
    private val clock: Clock,
) {
    private val zone = ZoneId.of("Asia/Kolkata")

    suspend fun options(bookingId: UUID, customerId: UUID): WorkerOptionsSnapshot {
        val booking = bookings.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        if (booking.customerId != customerId) throw CustomerAccessDenied()
        if (booking.status !in setOf(BookingStatus.AWAITING_WORKER, BookingStatus.AWAITING_PAYMENT)) {
            throw InvalidBookingState("Worker options are available only before payment")
        }
        val visits = holds.findAllByBookingIdOrderByVisitIndex(bookingId)
        if (visits.isEmpty() || visits.any { it.status != SlotHoldStatus.HELD || !it.expiresAt.isAfter(clock.instant()) }) {
            throw InvalidBookingState("Booking slot holds are no longer active")
        }
        val latitude = booking.serviceLatitude?.toDouble()
            ?: throw InvalidBookingState("Booking has no verified service location")
        val longitude = booking.serviceLongitude?.toDouble()
            ?: throw InvalidBookingState("Booking has no verified service location")
        val household = clients.household(customerId)
        val favouriteIds = household.favouriteWorkerIds.takeIf { household.plusActive }.orEmpty().take(3)
        val offering = catalog.requireActive(booking.serviceId)
        val evaluations = clients.workerOptions(
            query(offering.code, offering.basePricePerHourMinor, visits, latitude, longitude, booking.requirements, favouriteIds),
        )
        val byId = evaluations.associateBy(WorkerOptionEvaluationResponse::workerId)
        val favourites = favouriteIds.mapNotNull(byId::get).map(::snapshot)
        val veterans = evaluations.asSequence()
            .filter { it.tier == "VETERAN" && it.workerId !in favouriteIds && it.selectable }
            .sortedWith(
                compareBy<WorkerOptionEvaluationResponse>(WorkerOptionEvaluationResponse::distanceKm)
                    .thenByDescending(WorkerOptionEvaluationResponse::rankScore)
                    .thenBy { it.workerId.toString() },
            )
            .take(2)
            .map(::snapshot)
            .toList()
        return WorkerOptionsSnapshot(booking.id, favourites, veterans)
    }

    private fun query(
        serviceCode: String,
        basePricePerHourMinor: Long,
        visits: List<SlotHold>,
        latitude: Double,
        longitude: Double,
        requirements: BookingRequirements,
        favouriteIds: List<UUID>,
    ) = WorkerOptionsQueryRequest(
        serviceCode = serviceCode,
        basePricePerHourMinor = basePricePerHourMinor,
        visits = visits.map { WorkerOptionVisitQuery(it.visitIndex, it.startsAt, it.durationMinutes) },
        latitude = latitude,
        longitude = longitude,
        maxDistanceKm = 15.0,
        hasPets = requirements.hasPets,
        foodContext = requirements.foodContext,
        utensilPreferences = requirements.utensilPreferences,
        preferredLanguages = requirements.preferredLanguages,
        allergies = requirements.allergies,
        religiousFoodRestrictions = requirements.religiousFoodRestrictions,
        workerConstraints = requirements.workerConstraints,
        heavyLiftingRequired = requirements.heavyLiftingRequired,
        disallowedChemicals = requirements.disallowedChemicals,
        requiredEquipment = requirements.requiredEquipment,
        accessibilityNeeds = requirements.accessibilityNeeds,
        favouriteWorkerIds = favouriteIds,
    )

    private fun snapshot(candidate: WorkerOptionEvaluationResponse): WorkerOptionSnapshot {
        val visits = candidate.visits.sortedBy { it.visitIndex }.map { visit ->
            val local = visit.startsAt.atZone(zone)
            WorkerOptionVisitSnapshot(
                visit.visitIndex,
                local.toLocalDate(),
                local.toLocalTime(),
                visit.durationMinutes,
                visit.available,
                visit.reasonCodes,
                visit.priceMinor,
            )
        }
        return WorkerOptionSnapshot(
            workerId = candidate.workerId,
            firstName = candidate.firstName,
            tier = candidate.tier,
            averageRating = candidate.averageRating,
            ratingCount = candidate.ratingCount,
            completedJobs = completions.completedCount(candidate.workerId).completedJobs,
            tenureMonths = candidate.tenureMonths,
            distanceKm = candidate.distanceKm,
            tierSurchargeMinor = candidate.tierSurchargeMinor,
            bookingTotalMinor = visits.sumOf(WorkerOptionVisitSnapshot::priceMinor),
            selectable = candidate.selectable,
            reasonCodes = candidate.reasonCodes,
            visits = visits,
        )
    }
}
