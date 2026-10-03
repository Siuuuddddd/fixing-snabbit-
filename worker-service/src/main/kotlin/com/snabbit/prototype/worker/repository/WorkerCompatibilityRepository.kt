package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerCompatibility
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerCompatibilityRepository : JpaRepository<WorkerCompatibility, UUID> {
    fun findAllByWorkerIdIn(workerIds: Collection<UUID>): List<WorkerCompatibility>
}
