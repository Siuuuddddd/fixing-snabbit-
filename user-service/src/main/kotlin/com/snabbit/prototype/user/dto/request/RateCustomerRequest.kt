package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.util.UUID

data class RateCustomerRequest(
    val bookingId: UUID,
    @field:Min(0) @field:Max(6)
    val visitIndex: Int,
    @field:Min(1) @field:Max(5)
    val stars: Int,
    @field:Size(max = 500)
    val comment: String? = null,
)
