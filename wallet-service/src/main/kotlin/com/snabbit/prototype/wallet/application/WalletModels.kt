package com.snabbit.prototype.wallet.application

import java.time.Instant
import java.util.UUID

data class WalletBalance(
    val walletId: UUID,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val reservedMinor: Long,
    val availableMinor: Long,
    val totalSpentMinor: Long,
)

data class WalletOwnershipSnapshot(val walletId: UUID, val customerId: UUID)

data class TopUpSnapshot(
    val topUpId: UUID,
    val walletId: UUID,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

data class PlusPurchaseSnapshot(
    val purchaseId: UUID,
    val walletId: UUID,
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)

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

data class RefundSnapshot(
    val refundId: UUID,
    val bookingPaymentId: UUID,
    val status: String,
    val walletAmountMinor: Long,
    val onlineAmountMinor: Long,
    val providerRefundId: String?,
)
