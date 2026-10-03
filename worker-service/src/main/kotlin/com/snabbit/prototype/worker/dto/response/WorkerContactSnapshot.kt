package com.snabbit.prototype.worker.dto.response

import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class WorkerContactSnapshot(
    val workerId: UUID,
    val phoneNumber: String,
)
