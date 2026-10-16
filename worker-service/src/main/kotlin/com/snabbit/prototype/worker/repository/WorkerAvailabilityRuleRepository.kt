package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerAvailabilityRule
import com.snabbit.prototype.worker.domain.WorkerAvailabilityRuleId
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerAvailabilityRuleRepository : JpaRepository<WorkerAvailabilityRule, WorkerAvailabilityRuleId> {
    fun findAllByWorkerIdIn(workerIds: Collection<UUID>): List<WorkerAvailabilityRule>
}
