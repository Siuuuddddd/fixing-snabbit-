package com.snabbit.prototype.worker.dto.request

import java.time.Instant
import java.util.UUID

data class MatchWorkersCommand(
    val serviceCode: String,
    val basePricePerHourMinor: Long,
    val startsAt: Instant,
    val durationMinutes: Int,
    val latitude: Double,
    val longitude: Double,
    val maxDistanceKm: Double,
    val hasPets: Boolean,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val allergies: List<String>,
    val religiousFoodRestrictions: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
    val accessibilityNeeds: List<String>,
    val favouriteWorkerIds: List<UUID>,
    val plusActive: Boolean,
    val expertPreference: String = "ANY",
    val favouriteWorkerId: UUID? = null,
    val fallbackPolicy: String = "AUTO_ASSIGN",
    val requiredTier: String? = null,
)
