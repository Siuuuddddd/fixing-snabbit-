package com.snabbit.prototype.booking.domain

data class BookingRequirements(
    val hasPets: Boolean = false,
    val petDetails: String? = null,
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
