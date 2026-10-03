package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.enums.PlusPassStatus
import com.snabbit.prototype.user.domain.PlusPass
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlusPassRepository : JpaRepository<PlusPass, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pass from PlusPass pass where pass.heldForBookingId = :bookingId")
    fun findByBookingForUpdate(@Param("bookingId") bookingId: UUID): PlusPass?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select pass from PlusPass pass
        where pass.userId = :userId
          and pass.status = :status
          and pass.expiresAt >= :holdUntil
          and (pass.serviceCode is null or pass.serviceCode = :serviceCode)
        order by pass.expiresAt asc, pass.discountMinor desc
        """,
    )
    fun findEligibleForUpdate(
        @Param("userId") userId: UUID,
        @Param("serviceCode") serviceCode: String,
        @Param("status") status: PlusPassStatus,
        @Param("holdUntil") holdUntil: Instant,
    ): List<PlusPass>

    fun countByUserIdAndStatusAndExpiresAtAfter(userId: UUID, status: PlusPassStatus, now: Instant): Long

    fun findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAt(
        status: PlusPassStatus,
        now: Instant,
    ): List<PlusPass>
}
