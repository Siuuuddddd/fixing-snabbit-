package com.snabbit.prototype.wallet.support

import java.util.UUID

class WalletNotFound(id: UUID) : RuntimeException("Wallet $id was not found")
class WalletRuleViolation(message: String) : RuntimeException(message)
class PaymentNotFound(message: String) : RuntimeException(message)
class PaymentConflict(message: String) : RuntimeException(message)
class InvalidWebhook(message: String) : RuntimeException(message)
