package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import com.snabbit.prototype.worker.dto.response.WorkerMatch
import com.snabbit.prototype.worker.exception.InvalidMatchRequest
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRuleRepository
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerSkillRepository
import com.snabbit.prototype.worker.rating.WorkerRankingPolicy
import java.math.BigDecimal
import java.time.ZoneId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerMatchingService(
    private val profileRepository: WorkerProfileRepository,
    private val skillRepository: WorkerSkillRepository,
    private val availabilityRules: WorkerAvailabilityRuleRepository,
    private val compatibilityRepository: WorkerCompatibilityRepository,
    private val tierPolicy: WorkerTierPolicy,
    private val rankingPolicy: WorkerRankingPolicy,
    private val compatibilityPolicy: WorkerCompatibilityPolicy,
    private val locationPolicy: WorkerLocationPolicy,
) {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Transactional(readOnly = true)
    fun match(command: MatchWorkersCommand): List<WorkerMatch> {
        validate(command)
        val localStart = command.startsAt.atZone(zone)
        val profiles = profileRepository.findAllByStatus(WorkerStatus.ACTIVE).associateBy(WorkerProfile::id)
        val skills = skillRepository.findAllByIdServiceCodeAndEnabledTrue(command.serviceCode.uppercase())
            .associateBy { it.id.workerId }
        val availableWorkerIds = availabilityRules.findAllByWorkerIdIn(profiles.keys)
            .filter { it.containsStart(localStart.toLocalTime()) }
            .mapTo(mutableSetOf()) { it.workerId }
        val candidateIds = profiles.keys.intersect(skills.keys).intersect(availableWorkerIds)
        val compatibility = compatibilityRepository.findAllByWorkerIdIn(candidateIds).associateBy { it.workerId }
        val favouriteIds = command.favouriteWorkerIds.toSet()

        val ranked = candidateIds.mapNotNull { workerId ->
            val profile = profiles.getValue(workerId)
            val tier = tierPolicy.currentTier(profile)
            if (!compatibilityPolicy.evaluate(command, compatibility[workerId]).compatible) return@mapNotNull null
            val location = locationPolicy.evaluate(
                command.latitude,
                command.longitude,
                profile.currentLatitude.toDouble(),
                profile.currentLongitude.toDouble(),
                command.maxDistanceKm,
                profile.serviceRadiusKm.toDouble(),
            )
            if (!location.inRange) return@mapNotNull null
            val basePrice = command.basePricePerHourMinor * command.durationMinutes / 60
            RankedMatch(
                value = WorkerMatch(
                    workerId = profile.id,
                    displayName = profile.displayName,
                    tier = tier.name,
                    averageRating = profile.averageRating(),
                    distanceKm = location.displayDistanceKm,
                    favourite = workerId in favouriteIds,
                    priceMinor = basePrice + tierPolicy.surchargeMinor(tier),
                    tierSurchargeMinor = tierPolicy.surchargeMinor(tier),
                    rankScore = rankingPolicy.score(profile),
                ),
                score = score(profile, location.distanceKm, workerId in favouriteIds, command.plusActive),
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
                    val tier = profiles[favouriteId]?.let(tierPolicy::currentTier)?.name
                    val sameTier = tierEligible.filter { it.tier == tier }
                        .sortedWith(
                            compareBy<WorkerMatch>(WorkerMatch::distanceKm)
                                .thenByDescending(WorkerMatch::rankScore),
                        )
                        .map { it.copy(assignmentType = "FALLBACK_SAME_TIER") }
                    if (sameTier.isNotEmpty()) sameTier
                    else if (command.plusActive) {
                        tierEligible.filter { it.tier == "VETERAN" }
                            .sortedWith(
                                compareBy<WorkerMatch>(WorkerMatch::distanceKm)
                                    .thenByDescending(WorkerMatch::rankScore),
                            )
                            .map { it.copy(assignmentType = "FALLBACK_PLUS_UPGRADE") }
                    } else emptyList()
                }
            }
            else -> throw InvalidMatchRequest("Unsupported expert preference")
        }.take(10)
    }

    private fun score(profile: WorkerProfile, distance: Double, favourite: Boolean, plusActive: Boolean): Long {
        val favouriteScore = when {
            favourite && plusActive -> 100_000L
            favourite -> 10_000L
            else -> 0L
        }
        val ratingScore = rankingPolicy.score(profile).multiply(BigDecimal(1_000)).toLong()
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

    private data class RankedMatch(val value: WorkerMatch, val score: Long)
}
