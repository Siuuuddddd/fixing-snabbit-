package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.HouseholdSnapshot

import com.snabbit.prototype.user.dto.request.HouseholdCommand

import com.snabbit.prototype.user.service.HouseholdService
import com.snabbit.prototype.user.support.CustomerAccessDenied
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/users/{userId}/household")
class InternalHouseholdController(private val service: HouseholdService) {
    @GetMapping
    fun snapshot(@PathVariable userId: UUID): HouseholdSnapshot = service.snapshot(userId)
}

internal fun requireOwner(authenticatedUserId: UUID, requestedUserId: UUID) {
    if (authenticatedUserId != requestedUserId) throw CustomerAccessDenied()
}
