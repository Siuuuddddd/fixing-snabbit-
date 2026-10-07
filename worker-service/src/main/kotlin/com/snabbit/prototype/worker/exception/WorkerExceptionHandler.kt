package com.snabbit.prototype.worker.exception

import com.snabbit.prototype.platform.UnverifiedTrialRecipient
import com.snabbit.prototype.platform.VerificationProviderUnavailable
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class WorkerExceptionHandler {
    @ExceptionHandler(UnverifiedTrialRecipient::class)
    fun unverifiedTrial(error: UnverifiedTrialRecipient): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty()).also {
            it.setProperty("errorCode", "TWILIO_TRIAL_NUMBER_UNVERIFIED")
        }

    @ExceptionHandler(VerificationProviderUnavailable::class)
    fun providerUnavailable(error: VerificationProviderUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, error.message.orEmpty()).also {
            it.setProperty("errorCode", "OTP_PROVIDER_UNAVAILABLE")
        }
    @ExceptionHandler(WorkerProfileRequired::class)
    fun profileRequired(error: WorkerProfileRequired): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("errorCode", "WORKER_PROFILE_REQUIRED")
        }
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
