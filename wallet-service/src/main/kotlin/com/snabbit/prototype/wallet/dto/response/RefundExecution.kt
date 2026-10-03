package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class RefundExecution(
    val refundId: UUID,
    val providerPaymentId: String?,
    val onlineAmountMinor: Long,
)
