package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import java.time.LocalTime

data class WorkerAvailabilityRequest(
    val status: AvailabilityStatus,
    val availableFrom: LocalTime? = null,
    val availableUntil: LocalTime? = null,
)
