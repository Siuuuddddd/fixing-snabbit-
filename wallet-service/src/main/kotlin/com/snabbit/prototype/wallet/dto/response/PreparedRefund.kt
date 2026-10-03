package com.snabbit.prototype.wallet.dto.response

data class PreparedRefund(
    val snapshot: RefundSnapshot,
    val providerPaymentId: String?,
)
