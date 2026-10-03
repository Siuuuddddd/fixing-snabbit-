package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.UserAddress
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserAddressRepository : JpaRepository<UserAddress, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<UserAddress>
    fun countByUserId(userId: UUID): Long
}
