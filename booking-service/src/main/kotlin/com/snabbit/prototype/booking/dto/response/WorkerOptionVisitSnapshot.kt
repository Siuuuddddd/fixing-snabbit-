package com.snabbit.prototype.booking.dto.response

import java.time.LocalDate
import java.time.LocalTime

data class WorkerOptionVisitSnapshot(
    val visitIndex: Int,
    val date: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val available: Boolean,
    val reasonCodes: Set<String>,
    val priceMinor: Long,
)
