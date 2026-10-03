package com.snabbit.prototype.worker.dto.response

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class WorkerLocationSnapshot(
    val locationId: UUID,
    val workerId: UUID,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
    val capturedAt: Instant,
    val receivedAt: Instant,
)
