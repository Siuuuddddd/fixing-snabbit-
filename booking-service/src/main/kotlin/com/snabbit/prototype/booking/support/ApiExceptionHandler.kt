package com.snabbit.prototype.booking.support

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

class BookingRuleViolation(message: String) : RuntimeException(message)
class SlotUnavailable(message: String) : RuntimeException(message)
class BookingNotFound(id: UUID) : RuntimeException("Booking $id was not found")
class OfferUnavailable(message: String) : RuntimeException(message)
class InvalidBookingState(message: String) : RuntimeException(message)

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
}
