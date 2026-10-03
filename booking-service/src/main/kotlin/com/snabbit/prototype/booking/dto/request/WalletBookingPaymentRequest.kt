package com.snabbit.prototype.booking.dto.request

import java.util.UUID

data class WalletBookingPaymentRequest(
    val bookingId: UUID,
    val useWallet: Boolean,
)
