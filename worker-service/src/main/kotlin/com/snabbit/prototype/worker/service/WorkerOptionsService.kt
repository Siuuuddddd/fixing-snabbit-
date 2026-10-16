package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import com.snabbit.prototype.worker.dto.request.WorkerOptionsCommand
import com.snabbit.prototype.worker.dto.response.WorkerOptionEvaluation
import com.snabbit.prototype.worker.dto.response.WorkerOptionVisitEvaluation
import com.snabbit.prototype.worker.exception.InvalidMatchRequest
import com.snabbit.prototype.worker.rating.WorkerRankingPolicy
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRuleRepository
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerSkillRepository
import java.time.ZoneId
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerOptionsService(
    private val profiles: WorkerProfileRepository,
    private val skills: WorkerSkillRepository,
    private val availabilityRules: WorkerAvailabilityRuleRepository,
    private val compatibility: WorkerCompatibilityRepository,
    private val tiers: WorkerTierPolicy,
    private val ranking: WorkerRankingPolicy,
    private val compatibilityPolicy: WorkerCompatibilityPolicy,
    private val locationPolicy: WorkerLocationPolicy,
) {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Transactional(readOnly = true)
    fun options(command: WorkerOptionsCommand): List<WorkerOptionEvaluation> {
        validate(command)
        val activeProfiles = profiles.findAllByStatus(WorkerStatus.ACTIVE)
        val favouriteProfiles = profiles.findAllById(command.favouriteWorkerIds)
        val candidates = (activeProfiles + favouriteProfiles).associateBy(WorkerProfile::id)
        val candidateIds = candidates.keys
        val favouriteIds = command.favouriteWorkerIds.toSet()
        val supportedSkills = skills.findAllByIdServiceCodeAndEnabledTrue(command.serviceCode.uppercase())
            .mapTo(mutableSetOf()) { it.id.workerId }
        val preferences = compatibility.findAllByWorkerIdIn(candidateIds).associateBy { it.workerId }
        val rulesByWorker = availabilityRules.findAllByWorkerIdIn(candidateIds).groupBy { it.workerId }

        return candidates.values.mapNotNull { profile ->
            val tier = tiers.currentTier(profile)
            val favourite = profile.id in favouriteIds
            val representative = command.visits.first().toMatchCommand(command)
            val compatibilityResult = compatibilityPolicy.evaluate(representative, preferences[profile.id])
            val location = locationPolicy.evaluate(
                command.latitude,
                command.longitude,
                profile.currentLatitude.toDouble(),
                profile.currentLongitude.toDouble(),
                command.maxDistanceKm,
                profile.serviceRadiusKm.toDouble(),
            )
            val commonReasons = linkedSetOf<String>()
            if (profile.status != WorkerStatus.ACTIVE) commonReasons += "WORKER_INACTIVE"
            if (profile.id !in supportedSkills) commonReasons += "SERVICE_NOT_SUPPORTED"
            commonReasons += compatibilityResult.reasons.map(Enum<*>::name)
            if (!location.inRange) commonReasons += "OUT_OF_RANGE"

            val surcharge = tiers.surchargeMinor(tier)
            val visitEvaluations = command.visits.map { visit ->
                val localStart = visit.startsAt.atZone(zone)
                val scheduleAvailable = rulesByWorker[profile.id].orEmpty().any { it.containsStart(localStart.toLocalTime()) }
                val reasons = linkedSetOf<String>().apply {
                    addAll(commonReasons)
                    if (!scheduleAvailable) add("VISIT_UNAVAILABLE")
                }
                WorkerOptionVisitEvaluation(
                    visitIndex = visit.visitIndex,
                    startsAt = visit.startsAt,
                    durationMinutes = visit.durationMinutes,
                    available = reasons.isEmpty(),
                    reasonCodes = reasons,
                    priceMinor = command.basePricePerHourMinor * visit.durationMinutes / 60 + surcharge,
                )
            }
            val reasons = linkedSetOf<String>().apply {
                addAll(commonReasons)
                if (visitEvaluations.any { !it.available }) add("PARTIAL_OR_NO_AVAILABILITY")
            }
            WorkerOptionEvaluation(
                workerId = profile.id,
                firstName = profile.displayName.trim().substringBefore(' '),
                tier = tier.name,
                averageRating = profile.averageRating(),
                ratingCount = profile.ratingCount,
                tenureMonths = tiers.completedMonths(profile),
                distanceKm = location.displayDistanceKm,
                rankScore = ranking.score(profile),
                tierSurchargeMinor = surcharge,
                favourite = favourite,
                selectable = reasons.isEmpty(),
                reasonCodes = reasons,
                visits = visitEvaluations,
            )
        }
    }

    private fun validate(command: WorkerOptionsCommand) {
        if (command.serviceCode.isBlank()) throw InvalidMatchRequest("Service code is required")
        if (command.basePricePerHourMinor <= 0) throw InvalidMatchRequest("Base price must be positive")
        if (command.visits.isEmpty()) throw InvalidMatchRequest("At least one visit is required")
        if (command.visits.map { it.visitIndex }.distinct().size != command.visits.size) {
            throw InvalidMatchRequest("Visit indexes must be unique")
        }
        if (command.visits.any { it.durationMinutes !in setOf(60, 120, 180, 240) }) {
            throw InvalidMatchRequest("Duration must be 1 to 4 whole hours")
        }
        if (command.latitude !in -90.0..90.0 || command.longitude !in -180.0..180.0) {
            throw InvalidMatchRequest("Valid coordinates are required")
        }
        if (command.maxDistanceKm <= 0) throw InvalidMatchRequest("Maximum distance must be positive")
    }

    private fun com.snabbit.prototype.worker.dto.request.WorkerOptionVisitRequest.toMatchCommand(
        command: WorkerOptionsCommand,
    ) = MatchWorkersCommand(
        serviceCode = command.serviceCode,
        basePricePerHourMinor = command.basePricePerHourMinor,
        startsAt = startsAt,
        durationMinutes = durationMinutes,
        latitude = command.latitude,
        longitude = command.longitude,
        maxDistanceKm = command.maxDistanceKm,
        hasPets = command.hasPets,
        foodContext = command.foodContext,
        utensilPreferences = command.utensilPreferences,
        preferredLanguages = command.preferredLanguages,
        allergies = command.allergies,
        religiousFoodRestrictions = command.religiousFoodRestrictions,
        workerConstraints = command.workerConstraints,
        heavyLiftingRequired = command.heavyLiftingRequired,
        disallowedChemicals = command.disallowedChemicals,
        requiredEquipment = command.requiredEquipment,
        accessibilityNeeds = command.accessibilityNeeds,
        favouriteWorkerIds = command.favouriteWorkerIds,
        plusActive = true,
    )
}
