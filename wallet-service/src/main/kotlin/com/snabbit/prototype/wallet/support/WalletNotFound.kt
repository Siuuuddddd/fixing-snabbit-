package com.snabbit.prototype.wallet.support

import java.util.UUID

class WalletNotFound(id: UUID) : RuntimeException("Wallet $id was not found")
