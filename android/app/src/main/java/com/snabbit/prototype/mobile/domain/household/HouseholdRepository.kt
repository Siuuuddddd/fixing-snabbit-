package com.snabbit.prototype.mobile.domain.household

data class HouseholdPreferences(
    val hasPets: Boolean = false,
    val petDetails: String = "",
    val foodContext: String = "",
    val utensilPreferences: Set<String> = emptySet(),
    val preferredLanguages: Set<String> = emptySet(),
    val allergies: Set<String> = emptySet(),
    val religiousFoodRestrictions: Set<String> = emptySet(),
    val accessibilityNeeds: Set<String> = emptySet(),
    val workerConstraints: Set<String> = emptySet(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: Set<String> = emptySet(),
    val requiredEquipment: Set<String> = emptySet(),
    val plusActive: Boolean = false,
)

interface HouseholdRepository {
    suspend fun preferences(): HouseholdPreferences
    suspend fun update(preferences: HouseholdPreferences): HouseholdPreferences
}
