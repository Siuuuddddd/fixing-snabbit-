package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.dto.response.WorkerCompletionCountSnapshot
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookingCompletionQueryService(private val holds: SlotHoldRepository) {
    @Transactional(readOnly = true)
    fun completedWorkerIds(customerId: UUID): List<UUID> = holds.findCompletedWorkerIds(customerId)

    @Transactional(readOnly = true)
    fun completedCount(workerId: UUID): WorkerCompletionCountSnapshot = WorkerCompletionCountSnapshot(
        workerId,
        holds.countByWorkerIdAndJobStatus(workerId, JobStatus.COMPLETED),
    )
}
