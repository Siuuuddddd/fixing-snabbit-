package com.snabbit.prototype.worker.dto.request

import java.math.BigDecimal
import java.time.Instant

data class WorkerLocationCommand(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
    val capturedAt: Instant,
)
