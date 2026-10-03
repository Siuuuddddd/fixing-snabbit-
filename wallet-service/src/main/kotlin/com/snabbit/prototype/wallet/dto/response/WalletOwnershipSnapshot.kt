package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class WalletOwnershipSnapshot(val walletId: UUID, val customerId: UUID)
