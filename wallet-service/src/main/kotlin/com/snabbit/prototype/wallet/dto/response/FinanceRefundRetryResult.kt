package com.snabbit.prototype.wallet.dto.response

import java.util.UUID

data class FinanceRefundRetryResult(
    val actionId: UUID,
    val refund: RefundSnapshot,
)
