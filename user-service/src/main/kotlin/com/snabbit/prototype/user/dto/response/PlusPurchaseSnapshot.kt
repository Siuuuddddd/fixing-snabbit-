package com.snabbit.prototype.user.dto.response

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
