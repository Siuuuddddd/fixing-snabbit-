package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

data class VisitRequest(
    val workerId: UUID,
    val startsAt: Instant,
    @field:Min(1) @field:Max(4)
    val durationHours: Long? = null,
)
