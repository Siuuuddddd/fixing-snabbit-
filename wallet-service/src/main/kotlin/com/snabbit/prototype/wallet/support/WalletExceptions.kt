package com.snabbit.prototype.wallet.support

import java.util.UUID

class WalletNotFound(id: UUID) : RuntimeException("Wallet $id was not found")
class WalletRuleViolation(message: String) : RuntimeException(message)
class BookingQuoteUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class WalletAccessDenied : RuntimeException("Authenticated customer does not own this wallet")
class PaymentNotFound(message: String) : RuntimeException(message)
class PaymentConflict(message: String) : RuntimeException(message)
class InvalidWebhook(message: String) : RuntimeException(message)
