package com.snabbit.prototype.booking.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class WorkerTrackingSnapshot(
    val locationId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val workerId: UUID,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
    val capturedAt: Instant,
    val receivedAt: Instant,
)
