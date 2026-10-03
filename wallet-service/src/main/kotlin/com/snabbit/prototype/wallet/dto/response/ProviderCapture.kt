package com.snabbit.prototype.wallet.dto.response

data class ProviderCapture(
    val eventId: String,
    val eventType: String,
    val providerOrderId: String,
    val providerPaymentId: String,
    val amountMinor: Long,
)
