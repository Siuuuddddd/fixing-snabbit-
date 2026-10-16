package com.snabbit.prototype.worker.domain

import com.snabbit.prototype.worker.domain.enums.AvailabilityPeriod
import java.io.Serializable
import java.util.UUID

data class WorkerAvailabilityRuleId(
    var workerId: UUID = UUID(0, 0),
    var period: AvailabilityPeriod = AvailabilityPeriod.MORNING,
) : Serializable
