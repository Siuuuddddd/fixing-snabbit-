package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import com.snabbit.prototype.worker.dto.request.MatchWorkersRequest
import com.snabbit.prototype.worker.dto.request.WorkerOptionsCommand
import com.snabbit.prototype.worker.dto.request.WorkerOptionsRequest
import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot
import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot
import com.snabbit.prototype.worker.dto.response.WorkerMatch
import com.snabbit.prototype.worker.dto.response.WorkerOptionEvaluation
import com.snabbit.prototype.worker.service.WorkerDirectoryService
import com.snabbit.prototype.worker.service.WorkerLocationService
import com.snabbit.prototype.worker.service.WorkerMatchingService
import com.snabbit.prototype.worker.service.WorkerOptionsService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/workers")
class InternalWorkerController(
    private val matchingService: WorkerMatchingService,
    private val locations: WorkerLocationService,
    private val directory: WorkerDirectoryService,
    private val optionsService: WorkerOptionsService,
) {
    @PostMapping("/matches")
    fun match(@Valid @RequestBody request: MatchWorkersRequest): List<WorkerMatch> = matchingService.match(
        MatchWorkersCommand(
            serviceCode = request.serviceCode,
            basePricePerHourMinor = request.basePricePerHourMinor,
            startsAt = request.startsAt,
            durationMinutes = request.durationMinutes,
            latitude = request.latitude,
            longitude = request.longitude,
            maxDistanceKm = request.maxDistanceKm,
            hasPets = request.hasPets,
            foodContext = request.foodContext,
            utensilPreferences = request.utensilPreferences,
            preferredLanguages = request.preferredLanguages,
            allergies = request.allergies,
            religiousFoodRestrictions = request.religiousFoodRestrictions,
            workerConstraints = request.workerConstraints,
            heavyLiftingRequired = request.heavyLiftingRequired,
            disallowedChemicals = request.disallowedChemicals,
            requiredEquipment = request.requiredEquipment,
            accessibilityNeeds = request.accessibilityNeeds,
            favouriteWorkerIds = request.favouriteWorkerIds,
            plusActive = request.plusActive,
            expertPreference = request.expertPreference,
            favouriteWorkerId = request.favouriteWorkerId,
            fallbackPolicy = request.fallbackPolicy,
            requiredTier = request.requiredTier,
        ),
    )

    @PostMapping("/options")
    fun options(@Valid @RequestBody request: WorkerOptionsRequest): List<WorkerOptionEvaluation> =
        optionsService.options(
            WorkerOptionsCommand(
                request.serviceCode,
                request.basePricePerHourMinor,
                request.visits,
                request.latitude,
                request.longitude,
                request.maxDistanceKm,
                request.hasPets,
                request.foodContext,
                request.utensilPreferences,
                request.preferredLanguages,
                request.allergies,
                request.religiousFoodRestrictions,
                request.workerConstraints,
                request.heavyLiftingRequired,
                request.disallowedChemicals,
                request.requiredEquipment,
                request.accessibilityNeeds,
                request.favouriteWorkerIds,
            ),
        )

    @GetMapping("/{workerId}/location")
    fun latestLocation(@PathVariable workerId: UUID): org.springframework.http.ResponseEntity<WorkerLocationSnapshot> =
        locations.latest(workerId)
            ?.let { org.springframework.http.ResponseEntity.ok(it) }
            ?: org.springframework.http.ResponseEntity.notFound().build()

    @GetMapping("/{workerId}/contact")
    fun contact(@PathVariable workerId: UUID): org.springframework.http.ResponseEntity<WorkerContactSnapshot> =
        directory.contact(workerId)
            ?.let { org.springframework.http.ResponseEntity.ok(it) }
            ?: org.springframework.http.ResponseEntity.notFound().build()
}
