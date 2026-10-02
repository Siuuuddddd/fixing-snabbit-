package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.AvailabilityStatus
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
import com.snabbit.prototype.worker.domain.WorkerStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface WorkerProfileRepository : JpaRepository<WorkerProfile, UUID> {
    fun findAllByStatus(status: WorkerStatus): List<WorkerProfile>
    fun findByPhoneE164(phoneE164: String): WorkerProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select worker from WorkerProfile worker where worker.id = :id")
    fun findForUpdate(@Param("id") id: UUID): WorkerProfile?
}

interface WorkerLocationRepository : JpaRepository<WorkerLocationPoint, UUID> {
    fun findFirstByWorkerIdOrderByReceivedAtDesc(workerId: UUID): WorkerLocationPoint?
}

interface WorkerRatingRepository : JpaRepository<WorkerRating, UUID> {
    fun findByBookingIdAndVisitIndex(bookingId: UUID, visitIndex: Int): WorkerRating?
}

interface WorkerSkillRepository : JpaRepository<WorkerSkill, WorkerSkillId> {
    fun findAllByIdServiceCodeAndEnabledTrue(serviceCode: String): List<WorkerSkill>
}

interface WorkerAvailabilityRepository : JpaRepository<WorkerAvailability, UUID> {
    fun findAllByServiceDateAndStatus(date: LocalDate, status: AvailabilityStatus): List<WorkerAvailability>
    fun findByWorkerIdAndServiceDate(workerId: UUID, serviceDate: LocalDate): WorkerAvailability?
    fun findAllByWorkerIdAndServiceDateBetweenOrderByServiceDate(
        workerId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<WorkerAvailability>
}

interface WorkerCompatibilityRepository : JpaRepository<WorkerCompatibility, UUID> {
    fun findAllByWorkerIdIn(workerIds: Collection<UUID>): List<WorkerCompatibility>
}
