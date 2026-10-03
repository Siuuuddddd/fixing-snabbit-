package com.snabbit.prototype.booking.dto.request

import java.math.BigDecimal

data class WorkerLocationCommand(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
)
