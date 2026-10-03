package com.snabbit.prototype.wallet.dto.response

import java.time.Instant
import java.util.UUID

data class FinanceRefundSnapshot(
    val refundId: UUID,
    val bookingPaymentId: UUID,
    val walletId: UUID,
    val status: String,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val providerRefundId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
