package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.util.UUID

data class BookingPaymentSnapshot(
    val paymentId: UUID,
    val bookingId: UUID,
    val walletId: UUID,
    val totalAmountMinor: Long,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val status: String,
    val providerOrderId: String?,
    val reservationExpiresAt: Instant?,
)
