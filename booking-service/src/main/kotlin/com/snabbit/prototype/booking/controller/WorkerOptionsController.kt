package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.response.WorkerOptionsSnapshot
import com.snabbit.prototype.booking.service.WorkerOptionsService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class WorkerOptionsController(private val service: WorkerOptionsService) {
    @GetMapping("/{bookingId}/worker-options")
    suspend fun options(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): WorkerOptionsSnapshot = service.options(bookingId, customerId)
}
