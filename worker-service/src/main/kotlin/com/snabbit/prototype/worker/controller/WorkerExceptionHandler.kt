package com.snabbit.prototype.worker.controller

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

    @ExceptionHandler(InvalidWorkerAvailability::class)
    fun invalidAvailability(error: InvalidWorkerAvailability): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(WorkerRatingRejected::class)
    fun ratingRejected(error: WorkerRatingRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
