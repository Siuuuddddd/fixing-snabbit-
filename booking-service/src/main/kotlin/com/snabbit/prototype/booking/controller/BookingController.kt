package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.service.BookingLifecycleService
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController(private val service: BookingLifecycleService) {
    @GetMapping("/{bookingId}")
    fun get(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): BookingSnapshot = service.get(bookingId).also { requireOwner(authenticatedCustomerId, it.customerId) }

    @DeleteMapping("/{bookingId}/hold")
    fun cancelHold(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): BookingSnapshot = service.cancel(bookingId, authenticatedCustomerId)
}
