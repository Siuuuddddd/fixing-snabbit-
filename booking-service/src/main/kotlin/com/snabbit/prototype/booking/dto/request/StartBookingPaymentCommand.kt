package com.snabbit.prototype.booking.dto.request

import java.util.UUID

data class StartBookingPaymentCommand(
    val walletId: UUID,
    val bookingId: UUID,
    val authenticatedCustomerId: UUID,
    val useWallet: Boolean,
    val idempotencyKey: String,
)
