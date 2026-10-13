package com.snabbit.prototype.worker.dto.response

import java.math.BigDecimal
import java.util.UUID

data class WorkerMatch(
    val workerId: UUID,
    val displayName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val distanceKm: BigDecimal,
    val favourite: Boolean,
    val priceMinor: Long,
    val assignmentType: String = "PREFERRED",
    val tierSurchargeMinor: Long = 0,
)
