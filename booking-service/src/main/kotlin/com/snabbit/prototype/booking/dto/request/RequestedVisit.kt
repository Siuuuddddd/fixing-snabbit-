package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDate
import java.time.LocalTime

data class RequestedVisit(
    val date: LocalDate,
    val startTime: LocalTime,
    @field:Min(1) @field:Max(4)
    val durationHours: Long? = null,
)
