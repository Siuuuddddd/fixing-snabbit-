package com.snabbit.prototype.wallet.dto.request

import java.util.UUID

data class CreateBookingPaymentRequest(
    val bookingId: UUID,
    val useWallet: Boolean = true,
)
