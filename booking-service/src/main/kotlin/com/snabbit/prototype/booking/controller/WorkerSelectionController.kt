package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.WorkerSelectionRequest
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.service.WorkerSelectionService
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class WorkerSelectionController(private val service: WorkerSelectionService) {
    @PutMapping("/{bookingId}/worker-selection")
    suspend fun select(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @RequestBody(required = false) request: WorkerSelectionRequest?,
    ): BookingSnapshot = service.select(bookingId, customerId, request?.workerId)
}
