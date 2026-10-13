package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerRating
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerRatingRepository : JpaRepository<WorkerRating, UUID> {
    fun findByBookingIdAndVisitIndex(bookingId: UUID, visitIndex: Int): WorkerRating?
    fun findFirstByCustomerIdAndWorkerId(customerId: UUID, workerId: UUID): WorkerRating?
}
