package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class RefundSnapshot(
    val refundId: UUID,
    val bookingPaymentId: UUID,
    val status: String,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val providerRefundId: String?,
)
