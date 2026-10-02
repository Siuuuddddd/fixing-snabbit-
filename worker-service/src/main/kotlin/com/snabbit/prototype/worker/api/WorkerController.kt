package com.snabbit.prototype.worker.api

import com.snabbit.prototype.worker.application.InvalidMatchRequest
import com.snabbit.prototype.worker.application.MatchWorkersCommand
import com.snabbit.prototype.worker.application.WorkerMatch
import com.snabbit.prototype.worker.application.WorkerMatchingService
import com.snabbit.prototype.worker.application.InvalidWorkerLocation
import com.snabbit.prototype.worker.application.WorkerLocationCommand
import com.snabbit.prototype.worker.application.WorkerLocationService
import com.snabbit.prototype.worker.application.WorkerLocationSnapshot
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

data class MatchWorkersRequest(
    @field:NotBlank
    val serviceCode: String,
    @field:Min(1)
    val basePricePerHourMinor: Long,
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

data class WorkerLocationRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: BigDecimal,
    @field:Positive
    val accuracyMeters: BigDecimal,
    val capturedAt: Instant,
)

@RestController
@RequestMapping("/internal/v1/workers")
class InternalWorkerController(
    private val matchingService: WorkerMatchingService,
    private val locations: WorkerLocationService,
) {
    @PostMapping("/matches")
    fun match(@Valid @RequestBody request: MatchWorkersRequest): List<WorkerMatch> = matchingService.match(
        MatchWorkersCommand(
            request.serviceCode,
            request.basePricePerHourMinor,
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

    @GetMapping("/{workerId}/location")
    fun latestLocation(@PathVariable workerId: UUID): org.springframework.http.ResponseEntity<WorkerLocationSnapshot> =
        locations.latest(workerId)
            ?.let { org.springframework.http.ResponseEntity.ok(it) }
            ?: org.springframework.http.ResponseEntity.notFound().build()
}

@RestController
@RequestMapping("/api/v1/workers/me")
class WorkerLocationController(private val locations: WorkerLocationService) {
    @PostMapping("/location")
    fun updateLocation(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @Valid @RequestBody request: WorkerLocationRequest,
    ): WorkerLocationSnapshot = locations.update(
        workerId,
        WorkerLocationCommand(request.latitude, request.longitude, request.accuracyMeters, request.capturedAt),
    )
}

@RestControllerAdvice
class WorkerExceptionHandler {
    @ExceptionHandler(InvalidMatchRequest::class)
    fun invalidRequest(error: InvalidMatchRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(InvalidWorkerOtp::class)
    fun invalidOtp(error: InvalidWorkerOtp): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty())

    @ExceptionHandler(WorkerOtpRateLimited::class)
    fun otpRateLimited(error: WorkerOtpRateLimited): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, error.message.orEmpty())

    @ExceptionHandler(InvalidWorkerLocation::class)
    fun invalidLocation(error: InvalidWorkerLocation): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(WorkerRatingRejected::class)
    fun ratingRejected(error: WorkerRatingRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
