package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.response.WorkerMatch

import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot

import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot

import com.snabbit.prototype.worker.dto.request.MatchWorkersRequest

import com.snabbit.prototype.worker.dto.request.WorkerLocationCommand

import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand

import com.snabbit.prototype.worker.service.InvalidMatchRequest
import com.snabbit.prototype.worker.service.WorkerMatchingService
import com.snabbit.prototype.worker.service.InvalidWorkerLocation
import com.snabbit.prototype.worker.service.InvalidWorkerAvailability
import com.snabbit.prototype.worker.service.WorkerLocationService
import com.snabbit.prototype.worker.service.WorkerDirectoryService
import com.snabbit.prototype.worker.auth.InvalidWorkerOtp
import com.snabbit.prototype.worker.auth.WorkerOtpRateLimited
import com.snabbit.prototype.worker.rating.WorkerRatingRejected
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/workers")
class InternalWorkerController(
    private val matchingService: WorkerMatchingService,
    private val locations: WorkerLocationService,
    private val directory: WorkerDirectoryService,
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
