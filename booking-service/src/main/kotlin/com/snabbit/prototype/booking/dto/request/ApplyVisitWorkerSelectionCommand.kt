package com.snabbit.prototype.booking.dto.request

import java.util.UUID
import java.math.BigDecimal

data class ApplyVisitWorkerSelectionCommand(
    val bookingId: UUID,
    val customerId: UUID,
    val visitIndex: Int,
    val workerId: UUID,
    val tier: String,
    val tierSurchargeMinor: Long,
    val grossVisitPriceMinor: Long,
    val workerName: String,
    val workerRating: BigDecimal,
    val workerRatingCount: Int,
)
