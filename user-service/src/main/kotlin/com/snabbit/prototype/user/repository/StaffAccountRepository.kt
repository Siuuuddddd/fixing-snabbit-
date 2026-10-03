package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.StaffAccount
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface StaffAccountRepository : JpaRepository<StaffAccount, UUID> {
    fun findByPhoneE164(phoneE164: String): StaffAccount?
}
