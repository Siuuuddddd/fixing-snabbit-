package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.util.UUID

data class CancellationQuoteSnapshot(
    val bookingId: UUID,
    val plusActive: Boolean,
    val cancellationFeeMinor: Long,
    val refundAmountMinor: Long,
    val policyBand: String,
    val calculatedAt: Instant,
)
