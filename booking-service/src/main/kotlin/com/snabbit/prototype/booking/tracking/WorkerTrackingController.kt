package com.snabbit.prototype.booking.tracking

import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/visits/{visitIndex}/worker-location")
class WorkerTrackingController(private val tracking: WorkerTrackingService) {
    @GetMapping
    fun latest(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): WorkerTrackingSnapshot = tracking.latest(bookingId, visitIndex, customerId)

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): Flow<ServerSentEvent<WorkerTrackingSnapshot>> = tracking.stream(bookingId, visitIndex, customerId)
}
