package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerSession
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WorkerSessionRepository : JpaRepository<WorkerSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from WorkerSession session where session.refreshTokenHash = :hash")
    fun findByRefreshHashForUpdate(@Param("hash") hash: String): WorkerSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from WorkerSession session where session.id = :id")
    fun findForUpdate(@Param("id") id: UUID): WorkerSession?

    @Query("select session from WorkerSession session where session.workerId = :workerId and session.revokedAt is null order by session.createdAt asc")
    fun findActiveByWorkerId(@Param("workerId") workerId: UUID): List<WorkerSession>

    fun findByTokenFamilyId(tokenFamilyId: UUID): List<WorkerSession>
}
