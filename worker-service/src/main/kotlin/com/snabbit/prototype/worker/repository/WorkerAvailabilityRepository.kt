package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.domain.WorkerAvailability
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerAvailabilityRepository : JpaRepository<WorkerAvailability, UUID> {
    fun findAllByServiceDateAndStatus(date: LocalDate, status: AvailabilityStatus): List<WorkerAvailability>
    fun findByWorkerIdAndServiceDate(workerId: UUID, serviceDate: LocalDate): WorkerAvailability?
    fun findAllByWorkerIdAndServiceDateBetweenOrderByServiceDate(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<WorkerAvailability>
}
