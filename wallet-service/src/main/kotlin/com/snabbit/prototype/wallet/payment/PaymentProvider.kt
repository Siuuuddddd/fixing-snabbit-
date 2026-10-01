package com.snabbit.prototype.wallet.payment

data class ProviderOrder(val id: String, val amountMinor: Long, val currency: String)
data class ProviderRefund(val id: String, val amountMinor: Long)

interface PaymentProvider {
    fun createOrder(amountMinor: Long, receipt: String): ProviderOrder
    fun refund(paymentId: String, amountMinor: Long): ProviderRefund
}

class PaymentProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
