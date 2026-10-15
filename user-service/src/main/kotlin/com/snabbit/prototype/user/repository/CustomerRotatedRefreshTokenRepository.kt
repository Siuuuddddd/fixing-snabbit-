package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.CustomerRotatedRefreshToken
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRotatedRefreshTokenRepository : JpaRepository<CustomerRotatedRefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): CustomerRotatedRefreshToken?
}
