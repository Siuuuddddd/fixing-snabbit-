package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.WorkerCompatibilityReason

data class WorkerCompatibilityResult(
    val compatible: Boolean,
    val reasons: Set<WorkerCompatibilityReason>,
)
