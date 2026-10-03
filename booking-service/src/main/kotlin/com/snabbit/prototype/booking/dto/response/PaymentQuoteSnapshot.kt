package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class PaymentQuoteSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val grossTotalMinor: Long,
    val plusDiscountMinor: Long,
    val currency: String,
    val holdExpiresAt: Instant,
)
