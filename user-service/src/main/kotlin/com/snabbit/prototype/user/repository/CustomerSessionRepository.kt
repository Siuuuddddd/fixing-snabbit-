package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.CustomerSession
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CustomerSessionRepository : JpaRepository<CustomerSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from CustomerSession session where session.refreshTokenHash = :hash")
    fun findByRefreshHashForUpdate(@Param("hash") hash: String): CustomerSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from CustomerSession session where session.id = :id")
    fun findForUpdate(@Param("id") id: UUID): CustomerSession?

    @Query("select session from CustomerSession session where session.userId = :userId and session.revokedAt is null order by session.createdAt asc")
    fun findActiveByUserId(@Param("userId") userId: UUID): List<CustomerSession>

    fun findByTokenFamilyId(tokenFamilyId: UUID): List<CustomerSession>
}
