package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import com.snabbit.prototype.worker.dto.response.WorkerMatch
import com.snabbit.prototype.worker.exception.InvalidMatchRequest
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerSkillRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerMatchingService(
    private val profileRepository: WorkerProfileRepository,
    private val skillRepository: WorkerSkillRepository,
    private val availabilityRepository: WorkerAvailabilityRepository,
    private val compatibilityRepository: WorkerCompatibilityRepository,
) {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Transactional(readOnly = true)
    fun match(command: MatchWorkersCommand): List<WorkerMatch> {
        validate(command)
        val localStart = command.startsAt.atZone(zone)
        val localEnd = localStart.plusMinutes(command.durationMinutes.toLong())
        val profiles = profileRepository.findAllByStatus(WorkerStatus.ACTIVE).associateBy(WorkerProfile::id)
        val skills = skillRepository.findAllByIdServiceCodeAndEnabledTrue(command.serviceCode.uppercase())
            .associateBy { it.id.workerId }
        val availability = availabilityRepository
            .findAllByServiceDateAndStatus(localStart.toLocalDate(), AvailabilityStatus.AVAILABLE)
            .filter { it.availableFrom <= localStart.toLocalTime() && it.availableUntil >= localEnd.toLocalTime() }
            .associateBy { it.workerId }
        val candidateIds = profiles.keys.intersect(skills.keys).intersect(availability.keys)
        val compatibility = compatibilityRepository.findAllByWorkerIdIn(candidateIds).associateBy { it.workerId }
        val favouriteIds = command.favouriteWorkerIds.toSet()

        val ranked = candidateIds.mapNotNull { workerId ->
            val profile = profiles.getValue(workerId)
            val workerCompatibility = compatibility[workerId] ?: return@mapNotNull null
            if (!compatible(command, workerCompatibility)) return@mapNotNull null
            val distance = distanceKm(
                command.latitude,
                command.longitude,
                profile.currentLatitude.toDouble(),
                profile.currentLongitude.toDouble(),
            )
            if (distance > command.maxDistanceKm || distance > profile.serviceRadiusKm.toDouble()) return@mapNotNull null
            val basePrice = command.basePricePerHourMinor * command.durationMinutes / 60
            RankedMatch(
                value = WorkerMatch(
                    workerId = profile.id,
                    displayName = profile.displayName,
                    tier = profile.tier.name,
                    averageRating = profile.averageRating(),
                    distanceKm = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP),
                    favourite = workerId in favouriteIds,
                    priceMinor = basePrice + profile.tier.surchargeMinor,
                ),
                score = score(profile, distance, workerId in favouriteIds, command.plusActive),
            )
        }.sortedWith(
            compareByDescending<RankedMatch> { it.score }
                .thenBy { it.value.distanceKm }
                .thenBy { it.value.workerId.toString() },
        ).map(RankedMatch::value)
        val tierEligible = command.requiredTier?.uppercase()?.let { tier -> ranked.filter { it.tier == tier } } ?: ranked
        return when (command.expertPreference.uppercase()) {
            "ANY" -> tierEligible.map { it.copy(assignmentType = "PREFERRED") }
            "VETERAN" -> tierEligible.filter { it.tier == "VETERAN" }
                .map { it.copy(assignmentType = "PREFERRED") }
            "FAVOURITE" -> {
                val favouriteId = command.favouriteWorkerId
                    ?: throw InvalidMatchRequest("favouriteWorkerId is required for FAVOURITE preference")
                val favourite = tierEligible.filter { it.workerId == favouriteId }
                    .map { it.copy(assignmentType = "PREFERRED") }
                if (favourite.isNotEmpty() || command.fallbackPolicy.uppercase() == "SKIP_AND_REFUND") favourite
                else {
                    val tier = profiles[favouriteId]?.tier?.name
                    val sameTier = tierEligible.filter { it.tier == tier }
                        .sortedBy(WorkerMatch::distanceKm)
                        .map { it.copy(assignmentType = "FALLBACK_SAME_TIER") }
                    if (sameTier.isNotEmpty()) sameTier
                    else if (command.plusActive) {
                        tierEligible.filter { it.tier == "VETERAN" }
                            .sortedBy(WorkerMatch::distanceKm)
                            .map { it.copy(assignmentType = "FALLBACK_PLUS_UPGRADE") }
                    } else emptyList()
                }
            }
            else -> throw InvalidMatchRequest("Unsupported expert preference")
        }.take(10)
    }

    private fun compatible(command: MatchWorkersCommand, worker: WorkerCompatibility): Boolean {
        val languages = worker.languages.map(String::uppercase).toSet()
        val equipment = worker.availableEquipment.map(String::uppercase).toSet()
        val accessibility = worker.accessibilityCapabilities.map(String::uppercase).toSet()
        val utensilPreferences = worker.supportedUtensilPreferences.map(String::uppercase).toSet()
        val workerConstraints = worker.supportedWorkerConstraints.map(String::uppercase).toSet()
        val avoidedChemicals = worker.avoidedChemicals.map(String::uppercase).toSet()
        val requestedLanguages = command.preferredLanguages.map(String::uppercase).toSet()
        val requestedAllergyConstraints = command.allergies.map(String::uppercase).toSet()
        val requestedReligiousFoodRestrictions = command.religiousFoodRestrictions.map(String::uppercase).toSet()
        val requestedEquipment = command.requiredEquipment.map(String::uppercase).toSet()
        val requestedAccessibility = command.accessibilityNeeds.map(String::uppercase).toSet()
        val requestedUtensilPreferences = command.utensilPreferences.map(String::uppercase).toSet()
        val requestedWorkerConstraints = command.workerConstraints.map(String::uppercase).toSet()
        val disallowedChemicals = command.disallowedChemicals.map(String::uppercase).toSet()
        val supportedAllergyConstraints = worker.supportedAllergyConstraints.map(String::uppercase).toSet()
        val acceptedReligiousFoodRestrictions = worker.acceptedReligiousFoodRestrictions.map(String::uppercase).toSet()
        val foodContext = command.foodContext?.uppercase()
        return (!command.hasPets || worker.acceptsPets) &&
            (!command.heavyLiftingRequired || worker.canHeavyLift) &&
            (requestedLanguages.isEmpty() || requestedLanguages.any(languages::contains)) &&
            supportedAllergyConstraints.containsAll(requestedAllergyConstraints) &&
            acceptedReligiousFoodRestrictions.containsAll(requestedReligiousFoodRestrictions) &&
            utensilPreferences.containsAll(requestedUtensilPreferences) &&
            workerConstraints.containsAll(requestedWorkerConstraints) &&
            avoidedChemicals.containsAll(disallowedChemicals) &&
            equipment.containsAll(requestedEquipment) &&
            accessibility.containsAll(requestedAccessibility) &&
            (foodContext.isNullOrBlank() || worker.acceptedFoodContexts.any { it.equals(foodContext, true) })
    }

    private fun score(profile: WorkerProfile, distance: Double, favourite: Boolean, plusActive: Boolean): Long {
        val favouriteScore = when {
            favourite && plusActive -> 100_000L
            favourite -> 10_000L
            else -> 0L
        }
        val ratingScore = profile.averageRating().multiply(BigDecimal(1_000)).toLong()
        val distanceScore = ((50.0 - distance.coerceAtMost(50.0)) * 10).toLong()
        return favouriteScore + ratingScore + distanceScore
    }

    private fun validate(command: MatchWorkersCommand) {
        if (command.durationMinutes !in setOf(60, 120, 180, 240)) {
            throw InvalidMatchRequest("Duration must be 1 to 4 whole hours")
        }
        if (command.serviceCode.isBlank()) throw InvalidMatchRequest("Service code is required")
        if (command.basePricePerHourMinor <= 0) throw InvalidMatchRequest("Base price must be positive")
        if (command.latitude !in -90.0..90.0 || command.longitude !in -180.0..180.0) {
            throw InvalidMatchRequest("Valid coordinates are required")
        }
        if (command.maxDistanceKm <= 0) throw InvalidMatchRequest("Maximum distance must be positive")
    }

    private fun distanceKm(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val earthRadiusKm = 6_371.0
        val latitudeDistance = Math.toRadians(toLat - fromLat)
        val longitudeDistance = Math.toRadians(toLon - fromLon)
        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class RankedMatch(val value: WorkerMatch, val score: Long)
}
