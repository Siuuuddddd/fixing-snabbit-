package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class WebhookOutcome(
    val result: String,
    val compensationRefundId: UUID? = null,
    val providerPaymentId: String? = null,
    val refundAmountMinor: Long = 0,
)
