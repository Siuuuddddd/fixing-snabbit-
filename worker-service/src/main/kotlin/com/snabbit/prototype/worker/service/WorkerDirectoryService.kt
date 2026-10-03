package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerDirectoryService(private val workers: WorkerProfileRepository) {
    @Transactional(readOnly = true)
    fun contact(workerId: UUID): WorkerContactSnapshot? = workers.findById(workerId)
        .map { WorkerContactSnapshot(it.id, it.phoneE164) }
        .orElse(null)
}
