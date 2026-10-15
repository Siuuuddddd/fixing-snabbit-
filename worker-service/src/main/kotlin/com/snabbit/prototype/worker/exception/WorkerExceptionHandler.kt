package com.snabbit.prototype.worker.exception

import com.snabbit.prototype.platform.GoogleIdentityFailure
import com.snabbit.prototype.platform.GoogleIdentityVerificationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class WorkerExceptionHandler {
    @ExceptionHandler(GoogleIdentityVerificationException::class)
    fun invalidGoogleIdentity(error: GoogleIdentityVerificationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified").also {
            it.setProperty(
                "code",
                if (error.failure == GoogleIdentityFailure.EMAIL_NOT_VERIFIED) "EMAIL_NOT_VERIFIED" else "INVALID_GOOGLE_TOKEN",
            )
        }

    @ExceptionHandler(WorkerSessionRevoked::class)
    fun sessionRevoked(error: WorkerSessionRevoked): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty()).also {
            it.setProperty("code", "SESSION_REVOKED")
        }

    @ExceptionHandler(WorkerRefreshInProgress::class)
    fun refreshInProgress(error: WorkerRefreshInProgress): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "REFRESH_IN_PROGRESS")
            it.setProperty("retryable", true)
        }

    @ExceptionHandler(WorkerAuthRateLimited::class)
    fun authRateLimited(error: WorkerAuthRateLimited): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, error.message.orEmpty()).also {
            it.setProperty("code", "RATE_LIMITED")
        }

    @ExceptionHandler(SharedWorkerSessionRequired::class)
    fun sharedSessionRequired(error: SharedWorkerSessionRequired): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("code", "SHARED_WORKER_SESSION_REQUIRED")
        }

    @ExceptionHandler(WorkerNotRegistered::class)
    fun workerNotRegistered(error: WorkerNotRegistered): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("code", "WORKER_NOT_REGISTERED")
        }

    @ExceptionHandler(InvalidWorkerSelectionGrant::class)
    fun invalidSelection(error: InvalidWorkerSelectionGrant): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty()).also {
            it.setProperty("code", "INVALID_SELECTION_TOKEN")
        }

    @ExceptionHandler(WorkerProfileRequired::class)
    fun profileRequired(error: WorkerProfileRequired): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("errorCode", "WORKER_PROFILE_REQUIRED")
        }
    @ExceptionHandler(InvalidMatchRequest::class)
    fun invalidRequest(error: InvalidMatchRequest): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(InvalidWorkerLocation::class)
    fun invalidLocation(error: InvalidWorkerLocation): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(InvalidWorkerAvailability::class)
    fun invalidAvailability(error: InvalidWorkerAvailability): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(WorkerRatingRejected::class)
    fun ratingRejected(error: WorkerRatingRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(error.status, error.message.orEmpty()).also {
            it.setProperty("errorCode", error.errorCode)
        }
}
