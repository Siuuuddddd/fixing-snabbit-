package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.dto.request.CreateBookingCommand
import com.snabbit.prototype.booking.dto.request.MatchAndHoldCommand
import com.snabbit.prototype.booking.dto.request.ReservePlusPassRequest
import com.snabbit.prototype.booking.dto.request.VisitCommand
import com.snabbit.prototype.booking.dto.request.WorkerMatchRequest
import com.snabbit.prototype.booking.dto.response.AddressSnapshotResponse
import com.snabbit.prototype.booking.dto.response.BookingAvailabilitySnapshot
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.HouseholdSnapshotResponse
import com.snabbit.prototype.booking.dto.response.VisitAvailabilitySnapshot
import com.snabbit.prototype.booking.dto.response.WorkerMatchResponse
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.SlotUnavailable
import com.snabbit.prototype.booking.service.BookingLifecycleService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

@Service
class MatchAndHoldService(
    private val clients: MatchingClients,
    private val lifecycleService: BookingLifecycleService,
    private val catalog: ServiceCatalogService,
    private val properties: BookingProperties,
    private val clock: Clock,
    private val windowPolicy: BookingWindowPolicy,
) {
    suspend fun availability(command: MatchAndHoldCommand): BookingAvailabilitySnapshot {
        validateRequest(command)
        val offering = catalog.requireActive(command.serviceId)
        val (household, address) = loadCustomerContext(command)
        val candidateLists = findCandidates(command, address, offering.code, offering.basePricePerHourMinor, household)
        val commonWorkers = candidateLists
            .map { candidates -> candidates.map(WorkerMatchResponse::workerId).toSet() }
            .reduceOrNull(Set<UUID>::intersect)
            .orEmpty()
        return BookingAvailabilitySnapshot(
            serviceId = command.serviceId,
            durationMinutes = command.duration.toMinutes().toInt(),
            allVisitsAvailable = candidateLists.all(List<WorkerMatchResponse>::isNotEmpty),
            sameWorkerAvailableForAllVisits = commonWorkers.isNotEmpty(),
            visits = command.startsAt.zip(candidateLists) { startsAt, candidates ->
                VisitAvailabilitySnapshot(
                    startsAt,
                    candidates.isNotEmpty(),
                    candidates.size,
                    candidates.minOfOrNull(WorkerMatchResponse::priceMinor),
                )
            },
        )
    }

    suspend fun matchAndHold(command: MatchAndHoldCommand): BookingSnapshot {
        validateRequest(command)
        val idempotencyKey = validateIdempotencyKey(command.idempotencyKey)
        val requestFingerprint = fingerprint(command)
        lifecycleService.findIdempotent(command.customerId, idempotencyKey, requestFingerprint)?.let { return it }
        val offering = catalog.requireActive(command.serviceId)
        val (household, address) = loadCustomerContext(command)
        val candidateLists = findCandidates(command, address, offering.code, offering.basePricePerHourMinor, household)
        if (candidateLists.any(List<WorkerMatchResponse>::isEmpty)) {
            throw SlotUnavailable("No compatible worker is available for every requested visit")
        }

        val selectedWorkerIds = selectWorkers(candidateLists, household)
        val quotedTotalMinor = candidateLists.zip(selectedWorkerIds).sumOf { (candidates, workerId) ->
            candidates.first { it.workerId == workerId }.priceMinor
        }
        val bookingId = UUID.nameUUIDFromBytes(
            "${command.customerId}:$idempotencyKey".toByteArray(StandardCharsets.UTF_8),
        )
        val pass = clients.reservePass(
            command.customerId,
            ReservePlusPassRequest(
                bookingId,
                offering.code,
                quotedTotalMinor,
                clock.instant().plus(properties.slotHoldDuration).plusSeconds(30),
            ),
        )
        try {
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
                quotedTotalMinor = pass.netTotalMinor,
                serviceLatitude = address.latitude,
                serviceLongitude = address.longitude,
                serviceAddressId = address.addressId,
                serviceAddressLabel = address.label,
                serviceAddressLine = address.addressLine,
                serviceAddressPincode = address.pincode,
                bookingId = bookingId,
                grossTotalMinor = pass.grossTotalMinor,
                plusDiscountMinor = pass.discountMinor,
                plusPassId = pass.passId,
                idempotencyKey = idempotencyKey,
                requestFingerprint = requestFingerprint,
            ),
            )
        } catch (error: Exception) {
            lifecycleService.findIdempotent(command.customerId, idempotencyKey, requestFingerprint)?.let { return it }
            if (pass.passId != null) runCatching { clients.releasePass(command.customerId, bookingId) }
            throw error
        }
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

    private suspend fun findCandidates(
        command: MatchAndHoldCommand,
        address: AddressSnapshotResponse,
        serviceCode: String,
        basePricePerHourMinor: Long,
        household: HouseholdSnapshotResponse,
    ): List<List<WorkerMatchResponse>> = coroutineScope {
        command.startsAt.map { start ->
            async {
                clients.matches(
                    WorkerMatchRequest(
                        serviceCode = serviceCode,
                        basePricePerHourMinor = basePricePerHourMinor,
                        startsAt = start,
                        durationMinutes = command.duration.toMinutes().toInt(),
                        latitude = address.latitude.toDouble(),
                        longitude = address.longitude.toDouble(),
                        maxDistanceKm = 15.0,
                        hasPets = household.hasPets,
                        foodContext = household.foodContext,
                        utensilPreferences = household.utensilPreferences,
                        preferredLanguages = household.preferredLanguages,
                        allergies = household.allergies,
                        religiousFoodRestrictions = household.religiousFoodRestrictions,
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

    private suspend fun loadCustomerContext(
        command: MatchAndHoldCommand,
    ): Pair<HouseholdSnapshotResponse, AddressSnapshotResponse> = coroutineScope {
        val household = async { clients.household(command.customerId) }
        val address = async { clients.address(command.customerId, command.addressId) }
        household.await() to address.await()
    }

    private fun validateRequest(command: MatchAndHoldCommand) {
        when (command.bookingType) {
            BookingType.MULTI_DAY -> {
                if (command.startsAt.size !in 2..7) {
                    throw BookingRuleViolation("Multi-day bookings require 2 to 7 visits")
                }
                val dates = command.startsAt.map { it.atZone(properties.zoneId).toLocalDate() }
                if (dates.distinct().size != dates.size) {
                    throw BookingRuleViolation("Multi-day visits must occur on different dates")
                }
            }
            BookingType.INSTANT, BookingType.SCHEDULED -> if (command.startsAt.size != 1) {
                throw BookingRuleViolation("Instant and scheduled bookings require exactly one visit")
            }
        }
        command.startsAt.forEach { windowPolicy.validate(command.bookingType, it, command.duration) }
    }

    private fun validateIdempotencyKey(rawKey: String?): String {
        val key = rawKey?.trim().orEmpty()
        if (key.length !in 8..120 || key.any(Char::isISOControl)) {
            throw BookingRuleViolation("Idempotency-Key must contain 8 to 120 printable characters")
        }
        return key
    }

    private fun fingerprint(command: MatchAndHoldCommand): String {
        val canonical = listOf(
            command.customerId,
            command.serviceId,
            command.bookingType.name,
            command.duration.toMinutes(),
            command.addressId,
            command.startsAt.joinToString(",") { it.toString() },
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
