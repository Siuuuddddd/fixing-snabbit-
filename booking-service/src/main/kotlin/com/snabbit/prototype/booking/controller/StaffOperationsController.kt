package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.PlatformCancellationRequest
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.service.BookingLifecycleService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/staff/operations/bookings")
class StaffOperationsController(private val bookings: BookingLifecycleService) {
    @PostMapping("/{bookingId}/platform-cancellation")
    fun cancelForPlatformFailure(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-Staff-Id") staffId: UUID,
        @Valid @RequestBody request: PlatformCancellationRequest,
    ): BookingSnapshot = bookings.cancelConfirmedByPlatform(bookingId, request.reason, staffId)
}
