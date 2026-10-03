package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.enums.WorkerStatus

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus

import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerSkill
import com.snabbit.prototype.worker.domain.WorkerSkillId
import com.snabbit.prototype.worker.domain.WorkerLocationPoint
import com.snabbit.prototype.worker.domain.WorkerRating
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface WorkerAvailabilityRepository : JpaRepository<WorkerAvailability, UUID> {
    fun findAllByServiceDateAndStatus(date: LocalDate, status: AvailabilityStatus): List<WorkerAvailability>
    fun findByWorkerIdAndServiceDate(workerId: UUID, serviceDate: LocalDate): WorkerAvailability?
    fun findAllByWorkerIdAndServiceDateBetweenOrderByServiceDate(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<WorkerAvailability>
}
