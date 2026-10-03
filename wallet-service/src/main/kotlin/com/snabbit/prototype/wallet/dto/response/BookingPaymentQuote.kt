package com.snabbit.prototype.wallet.dto.response

import java.time.Instant
import java.util.UUID

data class BookingPaymentQuote(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val currency: String,
    val holdExpiresAt: Instant,
)
