package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.dto.response.ProviderRefund

import com.snabbit.prototype.wallet.dto.response.ProviderOrder

interface PaymentProvider {
    fun findOrderByReceipt(receipt: String): ProviderOrder?
    fun createOrder(amountMinor: Long, receipt: String): ProviderOrder
    fun refund(paymentId: String, amountMinor: Long, reference: String): ProviderRefund
}
