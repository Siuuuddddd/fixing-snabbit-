package com.snabbit.prototype.wallet.dto.response

import java.time.Instant
import java.util.UUID

data class TopUpSnapshot(
    val topUpId: UUID,
    val walletId: UUID,
    val amountMinor: Long,
    val status: String,
    val providerOrderId: String?,
)
