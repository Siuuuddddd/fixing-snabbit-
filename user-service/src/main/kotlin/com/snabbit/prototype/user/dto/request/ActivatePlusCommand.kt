package com.snabbit.prototype.user.dto.request

import java.time.Instant

data class ActivatePlusCommand(
    val sourceReference: String,
    val startsAt: Instant,
    val expiresAt: Instant,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String?,
)
