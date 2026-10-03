package com.snabbit.prototype.wallet.dto.response

import java.time.Instant
import java.util.UUID

data class WalletOwnershipSnapshot(val walletId: UUID, val customerId: UUID)
