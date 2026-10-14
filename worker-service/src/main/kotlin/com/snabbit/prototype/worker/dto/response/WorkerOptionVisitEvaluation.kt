package com.snabbit.prototype.worker.dto.response

import java.time.Instant

data class WorkerOptionVisitEvaluation(
    val visitIndex: Int,
    val startsAt: Instant,
    val durationMinutes: Int,
    val available: Boolean,
    val reasonCodes: Set<String>,
    val priceMinor: Long,
)
