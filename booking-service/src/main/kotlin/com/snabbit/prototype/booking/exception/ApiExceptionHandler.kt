package com.snabbit.prototype.booking.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ArrivalOtpRequired::class)
    fun arrivalOtpRequired(error: ArrivalOtpRequired): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "ARRIVAL_OTP_REQUIRED")
            it.setProperty("verificationEndpoint", error.verificationEndpoint)
        }

    @ExceptionHandler(HomeContactValidationException::class)
    fun homeContactValidation(error: HomeContactValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty()).also {
            it.setProperty("code", "INVALID_HOME_CONTACT")
            it.setProperty("fieldErrors", mapOf(error.field to error.message.orEmpty()))
        }

    @ExceptionHandler(PlanValidationException::class)
    fun planValidation(error: PlanValidationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty()).also {
            it.setProperty("code", "INVALID_VISITS")
            it.setProperty("visitErrors", error.visitErrors)
        }
    @ExceptionHandler(AtomicHoldUnavailable::class)
    fun atomicHoldUnavailable(error: AtomicHoldUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "ATOMIC_HOLD_UNAVAILABLE")
            it.setProperty("visitFailures", error.failures)
        }
    @ExceptionHandler(BookingRuleViolation::class)
    fun bookingRuleViolation(error: BookingRuleViolation): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(SlotUnavailable::class)
    fun slotUnavailable(error: SlotUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(BookingNotFound::class)
    fun bookingNotFound(error: BookingNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(OfferUnavailable::class, InvalidBookingState::class)
    fun lifecycleConflict(error: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(PaymentRequestRejected::class)
    fun paymentRejected(error: PaymentRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(error.status, error.message.orEmpty())

    @ExceptionHandler(PaymentServiceUnavailable::class)
    fun paymentUnavailable(error: PaymentServiceUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty())

    @ExceptionHandler(HomeContactResolutionUnavailable::class)
    fun homeContactUnavailable(error: HomeContactResolutionUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty()).also {
            it.setProperty("code", "HOME_CONTACT_UNAVAILABLE")
        }

    @ExceptionHandler(CustomerAccessDenied::class)
    fun accessDenied(error: CustomerAccessDenied): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty())

    @ExceptionHandler(WorkerTrackingUnavailable::class)
    fun trackingUnavailable(error: WorkerTrackingUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(WorkerLocationServiceUnavailable::class)
    fun trackingServiceUnavailable(error: WorkerLocationServiceUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty())
}
