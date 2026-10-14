package com.snabbit.prototype.worker.dto.response

import java.math.BigDecimal
import java.util.UUID

data class WorkerOptionEvaluation(
    val workerId: UUID,
    val firstName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val ratingCount: Int,
    val tenureMonths: Long,
    val distanceKm: BigDecimal,
    val rankScore: BigDecimal,
    val tierSurchargeMinor: Long,
    val favourite: Boolean,
    val selectable: Boolean,
    val reasonCodes: Set<String>,
    val visits: List<WorkerOptionVisitEvaluation>,
)
