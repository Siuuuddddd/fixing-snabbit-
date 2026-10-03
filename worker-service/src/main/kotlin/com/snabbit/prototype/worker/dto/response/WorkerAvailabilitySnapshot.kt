package com.snabbit.prototype.worker.dto.response

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import java.time.LocalDate
import java.time.LocalTime

data class WorkerAvailabilitySnapshot(
    val serviceDate: LocalDate,
    val availableFrom: LocalTime,
    val availableUntil: LocalTime,
    val status: AvailabilityStatus,
)
