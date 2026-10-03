package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.HouseholdSnapshot

import com.snabbit.prototype.user.dto.request.HouseholdCommand

import com.snabbit.prototype.user.dto.request.HouseholdRequest

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
@RequestMapping("/api/v1/users/{userId}/household")
class HouseholdController(private val service: HouseholdService) {
    @GetMapping
    fun snapshot(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): HouseholdSnapshot = service.snapshot(userId.also { requireOwner(authenticatedUserId, it) })

    @PutMapping
    fun update(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @Valid @RequestBody request: HouseholdRequest,
    ): HouseholdSnapshot = service.update(
        userId.also { requireOwner(authenticatedUserId, it) },
        HouseholdCommand(
            request.hasPets,
            request.petDetails,
            request.foodContext,
            request.utensilPreferences,
            request.preferredLanguages,
            request.allergies,
            request.religiousFoodRestrictions,
            request.accessibilityNeeds,
            request.workerConstraints,
            request.heavyLiftingRequired,
            request.disallowedChemicals,
            request.requiredEquipment,
        ),
    )

    @PostMapping("/favourites/{workerId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addFavourite(
        @PathVariable userId: UUID,
        @PathVariable workerId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): HouseholdSnapshot = service.addFavourite(userId.also { requireOwner(authenticatedUserId, it) }, workerId)

    @DeleteMapping("/favourites/{workerId}")
    fun removeFavourite(
        @PathVariable userId: UUID,
        @PathVariable workerId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): HouseholdSnapshot = service.removeFavourite(userId.also { requireOwner(authenticatedUserId, it) }, workerId)
}
