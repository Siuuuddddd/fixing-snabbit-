package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.UserProfile
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun findByPhoneE164(phoneE164: String): UserProfile?
    fun findByGoogleSub(googleSub: String): UserProfile?
    fun findByEmailIgnoreCase(email: String): UserProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserProfile user where lower(user.email) = lower(:email)")
    fun findByEmailForUpdate(@Param("email") email: String): UserProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserProfile user where user.id = :id")
    fun findForUpdate(@Param("id") id: UUID): UserProfile?
}
