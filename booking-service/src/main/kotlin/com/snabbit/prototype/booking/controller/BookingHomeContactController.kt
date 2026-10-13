package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.UpdateHomeContactRequest
import com.snabbit.prototype.booking.dto.response.HomeContactSnapshot
import com.snabbit.prototype.booking.service.HomeContactService
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class BookingHomeContactController(private val contacts: HomeContactService) {
    @PutMapping("/{bookingId}/home-contact")
    fun update(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestBody(required = false) request: UpdateHomeContactRequest?,
    ): HomeContactSnapshot = contacts.update(bookingId, authenticatedCustomerId, request)
}
