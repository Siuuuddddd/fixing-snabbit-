package com.snabbit.prototype.booking.dto.request

import java.time.Instant

data class WorkerOptionVisitQuery(
    val visitIndex: Int,
    val startsAt: Instant,
    val durationMinutes: Int,
)
