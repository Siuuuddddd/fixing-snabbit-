package com.snabbit.prototype.booking.dto.response

import java.math.BigDecimal
import java.util.UUID

data class WorkerOptionSnapshot(
    val workerId: UUID,
    val firstName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val ratingCount: Int,
    val completedJobs: Long,
    val tenureMonths: Long,
    val distanceKm: BigDecimal,
    val tierSurchargeMinor: Long,
    val bookingTotalMinor: Long,
    val selectable: Boolean,
    val reasonCodes: Set<String>,
    val visits: List<WorkerOptionVisitSnapshot>,
)
