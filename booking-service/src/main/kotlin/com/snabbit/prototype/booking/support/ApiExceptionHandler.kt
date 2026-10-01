package com.snabbit.prototype.booking.support

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class BookingRuleViolation(message: String) : RuntimeException(message)
class SlotUnavailable(message: String) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(BookingRuleViolation::class)
    fun bookingRuleViolation(error: BookingRuleViolation): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(SlotUnavailable::class)
    fun slotUnavailable(error: SlotUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
