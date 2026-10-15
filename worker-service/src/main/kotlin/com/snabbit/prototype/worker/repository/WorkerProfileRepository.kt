package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.WorkerProfile
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WorkerProfileRepository : JpaRepository<WorkerProfile, UUID> {
    fun findAllByStatus(status: WorkerStatus): List<WorkerProfile>
    fun findByPhoneE164(phoneE164: String): WorkerProfile?
    fun findByGoogleSubAndSeededFalse(googleSub: String): WorkerProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select worker from WorkerProfile worker where worker.seeded = false and lower(worker.email) = lower(:email)")
    fun findNonSeededByEmailForUpdate(@Param("email") email: String): List<WorkerProfile>

    @Query("select worker from WorkerProfile worker where worker.seeded = true and worker.id in :ids")
    fun findSeededByIds(@Param("ids") ids: Collection<UUID>): List<WorkerProfile>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select worker from WorkerProfile worker where worker.id = :id")
    fun findForUpdate(@Param("id") id: UUID): WorkerProfile?
}
