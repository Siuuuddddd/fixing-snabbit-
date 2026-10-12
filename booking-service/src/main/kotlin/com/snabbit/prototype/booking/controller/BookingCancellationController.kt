package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.CancelConfirmedBookingRequest
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.CancellationQuoteSnapshot
import com.snabbit.prototype.booking.service.BookingCancellationService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class BookingCancellationController(private val service: BookingCancellationService) {
    @GetMapping("/{bookingId}/cancellation-quote")
    suspend fun quote(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): CancellationQuoteSnapshot = service.quote(bookingId, authenticatedCustomerId)

    @PostMapping("/{bookingId}/cancellations")
    suspend fun cancel(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @Valid @RequestBody request: CancelConfirmedBookingRequest,
    ): BookingSnapshot = service.cancel(bookingId, authenticatedCustomerId, request.reason)

    @PostMapping("/{bookingId}/visits/{visitIndex}/cancellations")
    fun cancelVisit(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @Valid @RequestBody request: CancelConfirmedBookingRequest,
    ): BookingSnapshot = service.cancelVisit(bookingId, visitIndex, authenticatedCustomerId, request.reason)
}
