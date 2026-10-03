package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class WalletBalance(
    val walletId: UUID,
    val currency: String,
    val ledgerBalanceMinor: Long,
    val reservedMinor: Long,
    val availableMinor: Long,
    val totalSpentMinor: Long,
)
