package com.snabbit.prototype.worker.dto.response

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

data class WorkerMatch(
    val workerId: UUID,
    val displayName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val distanceKm: BigDecimal,
    val favourite: Boolean,
    val priceMinor: Long,
)
