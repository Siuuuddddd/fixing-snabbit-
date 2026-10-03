package com.snabbit.prototype.wallet.support

import java.util.UUID

class WalletAccessDenied : RuntimeException("Authenticated customer does not own this wallet")
