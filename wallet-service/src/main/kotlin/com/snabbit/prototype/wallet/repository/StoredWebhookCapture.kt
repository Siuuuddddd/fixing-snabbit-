package com.snabbit.prototype.wallet.repository

data class StoredWebhookCapture(
    val eventId: String,
    val eventType: String,
    val providerOrderId: String,
    val providerPaymentId: String,
    val amountMinor: Long,
)
