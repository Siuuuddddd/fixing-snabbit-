package com.snabbit.prototype.wallet.dto.request

import java.util.UUID

data class CreatePlusPurchaseRequest(
    val customerId: UUID,
    val planCode: String,
    val amountMinor: Long,
    val durationDays: Int,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String? = null,
)
