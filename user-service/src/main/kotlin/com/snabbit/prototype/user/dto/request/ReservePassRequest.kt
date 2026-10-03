package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ReservePassRequest(
    val bookingId: UUID,
    @field:NotBlank @field:Size(max = 60) val serviceCode: String,
    @field:Min(1) val grossTotalMinor: Long,
    val holdUntil: Instant,
)
