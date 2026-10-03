package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class CancellationQuoteSnapshot(
    val bookingId: UUID,
    val plusActive: Boolean,
    val cancellationFeeMinor: Long,
    val refundAmountMinor: Long,
    val policyBand: String,
    val calculatedAt: Instant,
)
