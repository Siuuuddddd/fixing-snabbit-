package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.application.BookingLifecycleService
import com.snabbit.prototype.booking.application.BookingSnapshot
import com.snabbit.prototype.booking.application.CreateBookingCommand
import com.snabbit.prototype.booking.application.VisitCommand
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.support.SlotUnavailable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class MatchAndHoldCommand(
    val customerId: UUID,
    val serviceId: UUID,
    val serviceCode: String,
    val bookingType: BookingType,
    val duration: Duration,
    val latitude: Double,
    val longitude: Double,
    val startsAt: List<Instant>,
)

@Service
class MatchAndHoldService(
    private val clients: MatchingClients,
    private val lifecycleService: BookingLifecycleService,
) {
    suspend fun matchAndHold(command: MatchAndHoldCommand): BookingSnapshot {
        val household = clients.household(command.customerId)
        val candidateLists = coroutineScope {
            command.startsAt.map { start ->
                async {
                    clients.matches(
                        WorkerMatchRequest(
                            serviceCode = command.serviceCode,
                            startsAt = start,
                            durationMinutes = command.duration.toMinutes().toInt(),
                            latitude = command.latitude,
                            longitude = command.longitude,
                            maxDistanceKm = 15.0,
                            hasPets = household.hasPets,
                            foodContext = household.foodContext,
                            utensilPreferences = household.utensilPreferences,
                            preferredLanguages = household.preferredLanguages,
                            workerConstraints = household.workerConstraints,
                            heavyLiftingRequired = household.heavyLiftingRequired,
                            disallowedChemicals = household.disallowedChemicals,
                            requiredEquipment = household.requiredEquipment,
                            accessibilityNeeds = household.accessibilityNeeds,
                            favouriteWorkerIds = household.favouriteWorkerIds,
                            plusActive = household.plusActive,
                        ),
                    )
                }
            }.awaitAll()
        }
        if (candidateLists.any(List<WorkerMatchResponse>::isEmpty)) {
            throw SlotUnavailable("No compatible worker is available for every requested visit")
        }

        val selectedWorkerIds = selectWorkers(candidateLists, household)
        val quotedTotalMinor = candidateLists.zip(selectedWorkerIds).sumOf { (candidates, workerId) ->
            candidates.first { it.workerId == workerId }.priceMinor
        }
        return lifecycleService.create(
            CreateBookingCommand(
                customerId = command.customerId,
                serviceId = command.serviceId,
                bookingType = command.bookingType,
                duration = command.duration,
                visits = command.startsAt.zip(selectedWorkerIds) { start, workerId -> VisitCommand(workerId, start) },
                requirements = BookingRequirements(
                    hasPets = household.hasPets,
                    petDetails = household.petDetails,
                    foodContext = household.foodContext,
                    utensilPreferences = household.utensilPreferences,
                    preferredLanguages = household.preferredLanguages,
                    allergies = household.allergies,
                    religiousFoodRestrictions = household.religiousFoodRestrictions,
                    accessibilityNeeds = household.accessibilityNeeds,
                    workerConstraints = household.workerConstraints,
                    heavyLiftingRequired = household.heavyLiftingRequired,
                    disallowedChemicals = household.disallowedChemicals,
                    requiredEquipment = household.requiredEquipment,
                ),
                quotedTotalMinor = quotedTotalMinor,
                serviceLatitude = BigDecimal.valueOf(command.latitude),
                serviceLongitude = BigDecimal.valueOf(command.longitude),
            ),
        )
    }

    internal fun selectWorkers(
        candidates: List<List<WorkerMatchResponse>>,
        household: HouseholdSnapshotResponse,
    ): List<UUID> {
        if (household.plusActive && candidates.size > 1) {
            val common = candidates.map { list -> list.map(WorkerMatchResponse::workerId).toSet() }
                .reduce(Set<UUID>::intersect)
            if (common.isNotEmpty()) {
                val favourite = household.favouriteWorkerIds.firstOrNull(common::contains)
                val continuousWorker = favourite ?: common.minBy { workerId ->
                    candidates.sumOf { list -> list.indexOfFirst { it.workerId == workerId } }
                }
                return List(candidates.size) { continuousWorker }
            }
        }
        return candidates.map { it.first().workerId }
    }
}
