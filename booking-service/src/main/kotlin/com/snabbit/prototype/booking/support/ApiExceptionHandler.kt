package com.snabbit.prototype.booking.support

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

class BookingRuleViolation(message: String) : RuntimeException(message)
class SlotUnavailable(message: String) : RuntimeException(message)
class BookingNotFound(id: UUID) : RuntimeException("Booking $id was not found")
class OfferUnavailable(message: String) : RuntimeException(message)
class InvalidBookingState(message: String) : RuntimeException(message)
class PaymentRequestRejected(val status: HttpStatusCode, message: String) : RuntimeException(message)
class PaymentServiceUnavailable(message: String, cause: Throwable) : RuntimeException(message, cause)
class CustomerAccessDenied : RuntimeException("Authenticated customer does not own this resource")

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
}
