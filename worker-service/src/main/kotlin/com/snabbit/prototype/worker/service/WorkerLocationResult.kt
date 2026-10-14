package com.snabbit.prototype.worker.service

import java.math.BigDecimal

data class WorkerLocationResult(
    val distanceKm: Double,
    val displayDistanceKm: BigDecimal,
    val inRange: Boolean,
)
