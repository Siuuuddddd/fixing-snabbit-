package com.snabbit.prototype.user.api

import com.snabbit.prototype.user.application.HouseholdCommand
import com.snabbit.prototype.user.application.HouseholdService
import com.snabbit.prototype.user.application.HouseholdSnapshot
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class HouseholdRequest(
    val hasPets: Boolean = false,
    @field:Size(max = 300)
    val petDetails: String? = null,
    @field:Size(max = 80)
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
)

@RestController
@RequestMapping("/api/v1/users/{userId}/household")
class HouseholdController(private val service: HouseholdService) {
    @GetMapping
    fun snapshot(@PathVariable userId: UUID): HouseholdSnapshot = service.snapshot(userId)

    @PutMapping
    fun update(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: HouseholdRequest,
    ): HouseholdSnapshot = service.update(
        userId,
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
    ): HouseholdSnapshot = service.addFavourite(userId, workerId)

    @DeleteMapping("/favourites/{workerId}")
    fun removeFavourite(
        @PathVariable userId: UUID,
        @PathVariable workerId: UUID,
    ): HouseholdSnapshot = service.removeFavourite(userId, workerId)
}
