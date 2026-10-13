package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.CustomerContactSnapshot
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerContactService(private val users: UserProfileRepository) {
    @Transactional(readOnly = true)
    fun contact(userId: UUID): CustomerContactSnapshot {
        val user = users.findById(userId).orElseThrow { UserNotFound(userId) }
        return CustomerContactSnapshot(user.id, user.phoneE164, user.displayName)
    }
}
