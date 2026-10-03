package com.snabbit.prototype.booking.support

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.snabbit.prototype.booking.tracking.WorkerTrackingUnavailable
import com.snabbit.prototype.booking.tracking.WorkerLocationServiceUnavailable
import java.util.UUID

class BookingNotFound(id: UUID) : RuntimeException("Booking $id was not found")
