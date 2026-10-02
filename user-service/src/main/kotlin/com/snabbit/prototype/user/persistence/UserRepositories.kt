package com.snabbit.prototype.user.persistence

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusPassStatus
import com.snabbit.prototype.user.domain.PlusSubscription
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun findByPhoneE164(phoneE164: String): UserProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserProfile user where user.id = :id")
    fun findForUpdate(@Param("id") id: UUID): UserProfile?
}

interface HouseholdPreferenceRepository : JpaRepository<HouseholdPreference, UUID>

interface FavouriteWorkerRepository : JpaRepository<FavouriteWorker, FavouriteWorkerId> {
    fun findAllByIdUserIdOrderByPriority(userId: UUID): List<FavouriteWorker>
}

interface CustomerRatingRepository : JpaRepository<CustomerRating, UUID> {
    fun findByBookingIdAndVisitIndex(bookingId: UUID, visitIndex: Int): CustomerRating?
}

interface PlusSubscriptionRepository : JpaRepository<PlusSubscription, UUID> {
    fun findBySourceReference(sourceReference: String): PlusSubscription?
    fun findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId: UUID, now: Instant): PlusSubscription?
}

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
