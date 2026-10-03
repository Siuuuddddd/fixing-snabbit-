package com.snabbit.prototype.booking.support

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.snabbit.prototype.booking.tracking.WorkerTrackingUnavailable
import com.snabbit.prototype.booking.tracking.WorkerLocationServiceUnavailable
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {
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
