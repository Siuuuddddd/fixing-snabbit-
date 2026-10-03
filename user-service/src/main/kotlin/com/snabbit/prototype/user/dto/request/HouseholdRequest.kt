package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Size

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
