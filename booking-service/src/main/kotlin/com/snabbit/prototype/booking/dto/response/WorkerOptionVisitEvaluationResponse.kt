package com.snabbit.prototype.booking.dto.response

import java.time.Instant

data class WorkerOptionVisitEvaluationResponse(
    val visitIndex: Int,
    val startsAt: Instant,
    val durationMinutes: Int,
    val available: Boolean,
    val reasonCodes: Set<String>,
    val priceMinor: Long,
)
