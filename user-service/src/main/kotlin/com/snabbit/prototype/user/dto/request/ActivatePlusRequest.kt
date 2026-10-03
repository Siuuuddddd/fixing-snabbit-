package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ActivatePlusRequest(
    @field:NotBlank @field:Size(max = 120) val sourceReference: String,
    val startsAt: Instant,
    val expiresAt: Instant,
    @field:Min(0) @field:Max(20) val passCount: Int,
    @field:Min(0) val passDiscountMinor: Long,
    @field:Size(max = 60) val serviceCode: String? = null,
)
