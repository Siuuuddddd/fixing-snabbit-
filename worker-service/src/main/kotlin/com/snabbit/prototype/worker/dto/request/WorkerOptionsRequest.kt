package com.snabbit.prototype.worker.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class WorkerOptionsRequest(
    @field:NotBlank
    val serviceCode: String,
    @field:Min(1)
    val basePricePerHourMinor: Long,
    @field:NotEmpty
    val visits: List<@Valid WorkerOptionVisitRequest>,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceKm: Double = 15.0,
    val hasPets: Boolean = false,
    val foodContext: String? = null,
    val utensilPreferences: List<String> = emptyList(),
    val preferredLanguages: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val religiousFoodRestrictions: List<String> = emptyList(),
    val workerConstraints: List<String> = emptyList(),
    val heavyLiftingRequired: Boolean = false,
    val disallowedChemicals: List<String> = emptyList(),
    val requiredEquipment: List<String> = emptyList(),
    val accessibilityNeeds: List<String> = emptyList(),
    val favouriteWorkerIds: List<UUID> = emptyList(),
)
