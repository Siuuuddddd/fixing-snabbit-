package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.HouseholdSnapshot
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.service.HouseholdService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/users/{userId}/household")
class InternalHouseholdController(private val service: HouseholdService) {
    @GetMapping
    fun snapshot(@PathVariable userId: UUID): HouseholdSnapshot = service.snapshot(userId)
}

internal fun requireOwner(authenticatedUserId: UUID, requestedUserId: UUID) {
    if (authenticatedUserId != requestedUserId) throw CustomerAccessDenied()
}
