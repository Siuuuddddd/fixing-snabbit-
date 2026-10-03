package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.dto.response.WorkerContactSnapshot

import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkerDirectoryService(private val workers: WorkerProfileRepository) {
    @Transactional(readOnly = true)
    fun contact(workerId: UUID): WorkerContactSnapshot? = workers.findById(workerId)
        .map { WorkerContactSnapshot(it.id, it.phoneE164) }
        .orElse(null)
}
