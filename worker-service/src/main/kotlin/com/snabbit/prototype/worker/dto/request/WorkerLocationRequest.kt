package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.dto.response.WorkerMatch

import com.snabbit.prototype.worker.dto.response.WorkerLocationSnapshot

import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot

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

data class WorkerLocationRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: BigDecimal,
    @field:Positive
    val accuracyMeters: BigDecimal,
    val capturedAt: Instant,
)
