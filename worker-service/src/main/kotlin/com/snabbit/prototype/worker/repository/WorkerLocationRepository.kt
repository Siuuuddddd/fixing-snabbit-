package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerLocationPoint
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerLocationRepository : JpaRepository<WorkerLocationPoint, UUID> {
    fun findFirstByWorkerIdOrderByReceivedAtDesc(workerId: UUID): WorkerLocationPoint?
}
