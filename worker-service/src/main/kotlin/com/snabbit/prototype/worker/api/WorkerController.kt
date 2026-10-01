package com.snabbit.prototype.worker.api

import com.snabbit.prototype.worker.application.InvalidMatchRequest
import com.snabbit.prototype.worker.application.MatchWorkersCommand
import com.snabbit.prototype.worker.application.WorkerMatch
import com.snabbit.prototype.worker.application.WorkerMatchingService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.UUID

data class MatchWorkersRequest(
    @field:NotBlank
    val serviceCode: String,
    val startsAt: Instant,
    @field:Min(60) @field:Max(240)
    val durationMinutes: Int,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceKm: Double = 15.0,
    val hasPets: Boolean = false,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val favouriteWorkerIds: List<UUID> = emptyList(),
    val plusActive: Boolean = false,
)

@RestController
@RequestMapping("/internal/v1/workers")
class InternalWorkerController(private val matchingService: WorkerMatchingService) {
    @PostMapping("/matches")
    fun match(@Valid @RequestBody request: MatchWorkersRequest): List<WorkerMatch> = matchingService.match(
        MatchWorkersCommand(
            request.serviceCode,
            request.startsAt,
            request.durationMinutes,
            request.latitude,
            request.longitude,
            request.maxDistanceKm,
            request.hasPets,
            request.foodContext,
            request.utensilPreferences,
            request.preferredLanguages,
            request.workerConstraints,
            request.heavyLiftingRequired,
            request.disallowedChemicals,
            request.requiredEquipment,
            request.accessibilityNeeds,
            request.favouriteWorkerIds,
            request.plusActive,
        ),
    )
}

@RestControllerAdvice
class WorkerExceptionHandler {
    @ExceptionHandler(InvalidMatchRequest::class)
    fun invalidRequest(error: InvalidMatchRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())
}
