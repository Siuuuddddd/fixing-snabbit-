package com.snabbit.prototype.wallet.exception

class WalletAccessDenied : RuntimeException("Authenticated customer does not own this wallet")
