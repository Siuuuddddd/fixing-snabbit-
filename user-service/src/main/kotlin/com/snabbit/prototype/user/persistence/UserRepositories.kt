package com.snabbit.prototype.user.persistence

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.domain.UserProfile
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
