package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

data class WorkerOptionVisitRequest(
    val visitIndex: Int,
    val startsAt: Instant,
    @field:Min(60) @field:Max(240)
    val durationMinutes: Int,
)
