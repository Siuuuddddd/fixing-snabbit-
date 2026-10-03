package com.snabbit.prototype.wallet.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class StoredWebhookCapture(
    val eventId: String,
    val eventType: String,
    val providerOrderId: String,
    val providerPaymentId: String,
    val amountMinor: Long,
)
