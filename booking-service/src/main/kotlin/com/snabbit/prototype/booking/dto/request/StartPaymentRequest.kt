package com.snabbit.prototype.booking.dto.request

import java.util.UUID

data class StartPaymentRequest(
    val walletId: UUID,
    val useWallet: Boolean = true,
)
