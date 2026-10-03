package com.snabbit.prototype.wallet.dto.response

import java.time.Instant
import java.util.UUID

data class PlusPurchaseSnapshot(
    val purchaseId: UUID,
    val walletId: UUID,
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)
