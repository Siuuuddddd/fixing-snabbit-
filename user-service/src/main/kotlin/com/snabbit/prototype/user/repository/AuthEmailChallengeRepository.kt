package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.AuthEmailChallenge
import com.snabbit.prototype.user.domain.enums.AuthEmailChallengePurpose
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuthEmailChallengeRepository : JpaRepository<AuthEmailChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select challenge from AuthEmailChallenge challenge where challenge.userId = :userId " +
            "and challenge.purpose = :purpose and challenge.consumedAt is null",
    )
    fun findActiveForUpdate(
        @Param("userId") userId: UUID,
        @Param("purpose") purpose: AuthEmailChallengePurpose,
    ): AuthEmailChallenge?

    @Modifying
    @Query(
        "update AuthEmailChallenge challenge set challenge.consumedAt = :at " +
            "where challenge.userId = :userId and challenge.purpose = :purpose and challenge.consumedAt is null",
    )
    fun consumeActive(
        @Param("userId") userId: UUID,
        @Param("purpose") purpose: AuthEmailChallengePurpose,
        @Param("at") at: Instant,
    ): Int
}
