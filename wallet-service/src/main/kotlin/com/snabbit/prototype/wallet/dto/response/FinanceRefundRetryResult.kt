package com.snabbit.prototype.wallet.dto.response

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import java.time.Instant
import java.util.UUID

data class FinanceRefundRetryResult(
    val actionId: UUID,
    val refund: RefundSnapshot,
)
