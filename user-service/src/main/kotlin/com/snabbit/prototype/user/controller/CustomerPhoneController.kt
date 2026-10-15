package com.snabbit.prototype.user.controller

import com.snabbit.prototype.platform.IndianPhoneNumber
import com.snabbit.prototype.user.dto.request.SaveCustomerPhoneRequest
import com.snabbit.prototype.user.dto.response.CustomerContactSnapshot
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.UserProfileRepository
import jakarta.validation.Valid
import java.time.Clock
import java.util.UUID
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/{userId}/phone")
class CustomerPhoneController(
    private val users: UserProfileRepository,
    private val clock: Clock,
) {
    @PutMapping
    @Transactional
    fun save(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @Valid @RequestBody request: SaveCustomerPhoneRequest,
    ): CustomerContactSnapshot {
        if (authenticatedUserId != userId) throw CustomerAccessDenied()
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        user.phoneE164 = IndianPhoneNumber.normalize(request.phoneNumber)
        user.updatedAt = clock.instant()
        return CustomerContactSnapshot(user.id, user.phoneE164, user.displayName)
    }
}
