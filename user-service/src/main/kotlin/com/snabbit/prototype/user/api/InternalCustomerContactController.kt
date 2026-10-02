package com.snabbit.prototype.user.api

import com.snabbit.prototype.user.application.UserNotFound
import com.snabbit.prototype.user.persistence.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CustomerContactSnapshot(val userId: UUID, val phoneNumber: String)

@Service
class CustomerContactService(private val users: UserProfileRepository) {
    @Transactional(readOnly = true)
    fun contact(userId: UUID): CustomerContactSnapshot {
        val user = users.findById(userId).orElseThrow { UserNotFound(userId) }
        return CustomerContactSnapshot(user.id, user.phoneE164)
    }
}

@RestController
@RequestMapping("/internal/v1/users")
class InternalCustomerContactController(private val service: CustomerContactService) {
    @GetMapping("/{userId}/contact")
    fun contact(@PathVariable userId: UUID): CustomerContactSnapshot = service.contact(userId)
}
