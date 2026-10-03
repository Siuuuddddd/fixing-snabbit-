package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.domain.enums.WorkerStatus

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus

import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerSkillRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
)
