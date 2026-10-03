package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import java.time.LocalTime

data class WorkerAvailabilityCommand(
    val status: AvailabilityStatus,
    val availableFrom: LocalTime?,
    val availableUntil: LocalTime?,
)
