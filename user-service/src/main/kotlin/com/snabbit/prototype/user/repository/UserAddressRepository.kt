package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.enums.PlusPassStatus

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.UserAddress
import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusSubscription
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserAddressRepository : JpaRepository<UserAddress, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<UserAddress>
    fun countByUserId(userId: UUID): Long
}
