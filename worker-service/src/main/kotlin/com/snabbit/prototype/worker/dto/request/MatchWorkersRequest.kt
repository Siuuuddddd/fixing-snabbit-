package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class MatchWorkersRequest(
    @field:NotBlank
    val serviceCode: String,
    @field:Min(1)
    val basePricePerHourMinor: Long,
    val startsAt: Instant,
    @field:Min(60) @field:Max(240)
    val durationMinutes: Int,
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
    val plusActive: Boolean = false,
)
