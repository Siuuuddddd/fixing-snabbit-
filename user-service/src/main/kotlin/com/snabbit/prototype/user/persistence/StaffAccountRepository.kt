package com.snabbit.prototype.user.persistence

import com.snabbit.prototype.user.domain.StaffAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StaffAccountRepository : JpaRepository<StaffAccount, UUID> {
    fun findByPhoneE164(phoneE164: String): StaffAccount?
}
