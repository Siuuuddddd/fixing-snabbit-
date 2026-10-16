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
import com.snabbit.prototype.booking.exception.AtomicHoldUnavailable
import com.snabbit.prototype.booking.exception.VisitHoldFailure
import com.snabbit.prototype.booking.exception.PlanValidationException
import com.snabbit.prototype.booking.exception.VisitValidationError
import com.snabbit.prototype.booking.exception.AtomicSlotReservationFailed
import com.snabbit.prototype.booking.exception.PlusRequired
import com.snabbit.prototype.booking.domain.enums.AssignmentType
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy
import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.repository.SlotHoldRepository
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
    private val holds: SlotHoldRepository? = null,
) {
    suspend fun availability(command: MatchAndHoldCommand): BookingAvailabilitySnapshot {
        validateRequest(command)
        val durations = resolvedDurations(command)
        val offering = catalog.requireActive(command.serviceId)
        val (household, address) = loadCustomerContext(command)
        validateFavourite(command, household)
        val allCandidateLists = findCandidates(
            command, address, offering.code, offering.basePricePerHourMinor, household, allTiers = true,
        )
        val candidateLists = if (command.expertPreference == ExpertPreference.FAVOURITE) {
            findCandidates(command, address, offering.code, offering.basePricePerHourMinor, household)
                .map { eligibleCandidates(command, it) }
        } else {
            allCandidateLists.map { eligibleCandidates(command, it) }
        }
        val commonWorkers = candidateLists
            .map { candidates -> candidates.map(WorkerMatchResponse::workerId).toSet() }
            .reduceOrNull(Set<UUID>::intersect)
            .orEmpty()
        return BookingAvailabilitySnapshot(
            serviceId = command.serviceId,
            durationMinutes = command.duration.toMinutes().toInt(),
            allVisitsAvailable = candidateLists.all(List<WorkerMatchResponse>::isNotEmpty),
            sameWorkerAvailableForAllVisits = commonWorkers.isNotEmpty(),
            visits = command.startsAt.mapIndexed { index, startsAt ->
                val candidates = candidateLists[index]
                val allCandidates = allCandidateLists[index]
                val selectedTier = resolvedTier(command, candidates)
                VisitAvailabilitySnapshot(
                    startsAt,
                    durations[index].toMinutes().toInt(),
                    candidates.isNotEmpty(),
                    candidates.size,
                    candidates.minOfOrNull(WorkerMatchResponse::priceMinor),
                    tierOptions = listOf("STANDARD", "VETERAN", "EXPERT").map { tier ->
                        val tierCandidates = allCandidates.filter { it.tier == tier }
                        com.snabbit.prototype.booking.dto.response.TierAvailabilitySnapshot(
                            tier = tier,
                            available = tierCandidates.isNotEmpty(),
                            compatibleWorkerCount = tierCandidates.size,
                            lowestPriceMinor = tierCandidates.minOfOrNull(WorkerMatchResponse::priceMinor),
                        )
                    },
                    selectedTier = selectedTier,
                )
            },
        )
    }

    suspend fun matchAndHold(command: MatchAndHoldCommand): BookingSnapshot {
        validateRequest(command)
        val durations = resolvedDurations(command)
        val idempotencyKey = validateIdempotencyKey(command.idempotencyKey)
        val requestFingerprint = fingerprint(command)
        lifecycleService.findIdempotent(command.customerId, idempotencyKey, requestFingerprint)?.let { return it }
        val offering = catalog.requireActive(command.serviceId)
        val (household, address) = loadCustomerContext(command)
        validateFavourite(command, household)
        val candidateLists = findCandidates(command, address, offering.code, offering.basePricePerHourMinor, household)
        val eligibleLists = candidateLists.map { eligibleCandidates(command, it) }
        if (eligibleLists.any(List<WorkerMatchResponse>::isEmpty)) {
            val failures = eligibleLists.mapIndexedNotNull { index, candidates ->
                if (candidates.isNotEmpty()) null else VisitHoldFailure(
                    index,
                    command.startsAt[index],
                    "No compatible worker is available for this visit and expert preference",
                    findAlternatives(command, index, address, offering.code, offering.basePricePerHourMinor, household),
                )
            }
            throw AtomicHoldUnavailable(failures)
        }

        val selected = eligibleLists.map { it.first() }
        val visitPrices = selected.mapIndexed { index, match ->
            cappedPrice(command, household, match, offering.basePricePerHourMinor, durations[index])
        }
        val quotedTotalMinor = visitPrices.sum()
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
            val netVisitPrices = allocateVisitPrices(visitPrices, pass.netTotalMinor)
            return lifecycleService.create(
                CreateBookingCommand(
                customerId = command.customerId,
                serviceId = command.serviceId,
                bookingType = command.bookingType,
                duration = command.duration,
                visits = command.startsAt.mapIndexed { index, start ->
                    val match = selected[index]
                    VisitCommand(
                        workerId = match.workerId,
                        startsAt = start,
                        duration = durations[index],
                        quotedPriceMinor = netVisitPrices[index],
                        assignmentType = AssignmentType.UNASSIGNED,
                        preferredWorkerId = command.favouriteWorkerId,
                        assignedWorkerTier = match.tier,
                        tierSurchargeMinor = if (
                            household.plusActive && properties.plusVeteranUpgradeFree &&
                            match.assignmentType == AssignmentType.FALLBACK_PLUS_UPGRADE.name
                        ) 0 else match.tierSurchargeMinor,
                    )
                },
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
                expertPreference = command.expertPreference,
                favouriteWorkerId = command.favouriteWorkerId,
                fallbackPolicy = command.fallbackPolicy,
            ),
            )
        } catch (error: Exception) {
            lifecycleService.findIdempotent(command.customerId, idempotencyKey, requestFingerprint)?.let { return it }
            if (pass.passId != null) runCatching { clients.releasePass(command.customerId, bookingId) }
            if (error is AtomicSlotReservationFailed) {
                val failures = error.failures.map { failure ->
                    VisitHoldFailure(
                        failure.visitIndex,
                        failure.startsAt,
                        failure.detail,
                        findAlternatives(
                            command,
                            failure.visitIndex,
                            address,
                            offering.code,
                            offering.basePricePerHourMinor,
                            household,
                        ),
                    )
                }
                throw AtomicHoldUnavailable(failures)
            }
            throw error
        }
    }

    private fun eligibleCandidates(
        command: MatchAndHoldCommand,
        candidates: List<WorkerMatchResponse>,
    ): List<WorkerMatchResponse> = when (command.expertPreference) {
        ExpertPreference.ANY -> command.workerTier?.let { requested ->
            candidates.filter { it.tier == requested.name }
        } ?: candidates
        ExpertPreference.VETERAN -> candidates.filter { it.tier == "VETERAN" }
        ExpertPreference.FAVOURITE -> {
            val favourite = candidates.filter { it.workerId == command.favouriteWorkerId }
            if (favourite.isNotEmpty() || command.fallbackPolicy == FallbackPolicy.SKIP_AND_REFUND) favourite
            else candidates
        }
    }

    private fun resolvedTier(
        command: MatchAndHoldCommand,
        candidates: List<WorkerMatchResponse>,
    ): String? = when (command.expertPreference) {
        ExpertPreference.FAVOURITE -> candidates.firstOrNull { it.workerId == command.favouriteWorkerId }?.tier
            ?: candidates.firstOrNull()?.tier
        ExpertPreference.VETERAN -> "VETERAN"
        ExpertPreference.ANY -> command.workerTier?.name
    }

    private fun validateFavourite(command: MatchAndHoldCommand, household: HouseholdSnapshotResponse) {
        if (command.expertPreference == ExpertPreference.FAVOURITE && !household.plusActive) {
            throw PlusRequired()
        }
        if (command.expertPreference == ExpertPreference.FAVOURITE &&
            command.favouriteWorkerId !in household.favouriteWorkerIds
        ) {
            throw BookingRuleViolation("The selected favourite worker is not in the customer's favourites")
        }
    }

    internal fun cappedPrice(
        command: MatchAndHoldCommand,
        household: HouseholdSnapshotResponse,
        match: WorkerMatchResponse,
        basePricePerHourMinor: Long,
        duration: Duration = command.duration,
    ): Long {
        val original = match.priceMinor
        val basePrice = basePricePerHourMinor * duration.toHours()
        val freeVeteran = household.plusActive && properties.plusVeteranUpgradeFree &&
            match.assignmentType == AssignmentType.FALLBACK_PLUS_UPGRADE.name
        return if (freeVeteran) minOf(basePrice, original) else original
    }

    private fun allocateVisitPrices(grossPrices: List<Long>, netTotalMinor: Long): List<Long> {
        if (grossPrices.isEmpty()) return emptyList()
        val result = grossPrices.toMutableList()
        var discount = grossPrices.sum() - netTotalMinor
        var index = result.lastIndex
        while (discount > 0 && index >= 0) {
            val reduction = minOf(result[index], discount)
            result[index] -= reduction
            discount -= reduction
            index--
        }
        check(discount == 0L) { "Plus discount exceeds visit prices" }
        return result
    }

    private suspend fun findAlternatives(
        command: MatchAndHoldCommand,
        failedIndex: Int,
        address: AddressSnapshotResponse,
        serviceCode: String,
        basePricePerHourMinor: Long,
        household: HouseholdSnapshotResponse,
    ): List<Instant> {
        val requested = command.startsAt[failedIndex]
        val duration = resolvedDurations(command)[failedIndex]
        val offsets = listOf(-45L, -30L, -15L, 15L, 30L, 45L, 60L)
        val alternatives = mutableListOf<Instant>()
        for (minutes in offsets) {
            val start = requested.plusSeconds(minutes * 60)
            if (runCatching { windowPolicy.validate(command.bookingType, start, duration) }.isFailure) continue
            val candidates = findCandidates(
                command.copy(startsAt = listOf(start), visitDurations = listOf(duration)),
                address,
                serviceCode,
                basePricePerHourMinor,
                household,
            )
                .single()
            if (eligibleCandidates(command, candidates).isNotEmpty()) alternatives += start
            if (alternatives.size == 3) break
        }
        return alternatives
    }

    private suspend fun findCandidates(
        command: MatchAndHoldCommand,
        address: AddressSnapshotResponse,
        serviceCode: String,
        basePricePerHourMinor: Long,
        household: HouseholdSnapshotResponse,
        allTiers: Boolean = false,
    ): List<List<WorkerMatchResponse>> = coroutineScope {
        val durations = resolvedDurations(command)
        command.startsAt.mapIndexed { index, start ->
            async {
                clients.matches(
                    WorkerMatchRequest(
                        serviceCode = serviceCode,
                        basePricePerHourMinor = basePricePerHourMinor,
                        startsAt = start,
                        durationMinutes = durations[index].toMinutes().toInt(),
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
                        expertPreference = if (allTiers) ExpertPreference.ANY.name else command.expertPreference.name,
                        favouriteWorkerId = if (allTiers) null else command.favouriteWorkerId,
                        fallbackPolicy = command.fallbackPolicy.name,
                        requiredTier = if (allTiers || command.expertPreference == ExpertPreference.FAVOURITE) {
                            null
                        } else {
                            command.workerTier?.name ?: command.expertPreference
                                .takeIf { it == ExpertPreference.VETERAN }
                                ?.name
                        },
                    ),
                ).filterNot { candidate ->
                    holds?.hasActiveOverlap(
                        candidate.workerId,
                        start,
                        start.plus(durations[index]),
                        clock.instant(),
                    ) == true
                }
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

    internal fun validateRequest(command: MatchAndHoldCommand) {
        if (command.fallbackPolicy == FallbackPolicy.ASK_FIRST) {
            throw BookingRuleViolation("ASK_FIRST is not supported; use AUTO_ASSIGN or SKIP_AND_REFUND")
        }
        if (command.expertPreference == ExpertPreference.FAVOURITE && command.favouriteWorkerId == null) {
            throw BookingRuleViolation("favouriteWorkerId is required when expertPreference is FAVOURITE")
        }
        if (command.visitDurations.isNotEmpty() && command.visitDurations.size != command.startsAt.size) {
            throw BookingRuleViolation("Every visit must have exactly one duration")
        }
        when (command.bookingType) {
            BookingType.MULTI_DAY -> {
                if (command.startsAt.size !in properties.minMultiDayVisits..properties.maxMultiDayVisits) {
                    throw BookingRuleViolation(
                        "Multi-day bookings require ${properties.minMultiDayVisits} to ${properties.maxMultiDayVisits} visits",
                    )
                }
            }
            BookingType.INSTANT, BookingType.SCHEDULED -> if (command.startsAt.size != 1) {
                throw BookingRuleViolation("Instant and scheduled bookings require exactly one visit")
            }
        }
        val duplicateDates = command.startsAt.groupBy { it.atZone(properties.zoneId).toLocalDate() }
            .filterValues { it.size > 1 }.keys
        val durations = resolvedDurations(command)
        val errors = command.startsAt.mapIndexedNotNull { index, start ->
            val duration = durations[index]
            val error = when {
                start.atZone(properties.zoneId).toLocalDate() in duplicateDates ->
                    "Multi-day visits must occur on different dates"
                duration.toMinutes() !in setOf(60L, 120L, 180L, 240L) ->
                    "Visit duration must be a whole hour between 1 and 4 hours"
                else -> runCatching { windowPolicy.validate(command.bookingType, start, duration) }
                    .exceptionOrNull()?.message
            }
            error?.let { VisitValidationError(index, start, it) }
        }
        if (errors.isNotEmpty()) throw PlanValidationException(errors)
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
            command.startsAt.zip(resolvedDurations(command)).joinToString(",") { (start, duration) ->
                "$start/${duration.toMinutes()}"
            },
            command.expertPreference.name,
            command.favouriteWorkerId,
            command.fallbackPolicy.name,
            command.workerTier?.name,
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun resolvedDurations(command: MatchAndHoldCommand): List<Duration> =
        command.visitDurations.takeIf(List<Duration>::isNotEmpty)
            ?: List(command.startsAt.size) { command.duration }
}
