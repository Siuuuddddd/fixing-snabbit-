package com.snabbit.prototype.booking.dto.response

import java.math.BigDecimal
import java.util.UUID

data class WorkerOptionEvaluationResponse(
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
    val visits: List<WorkerOptionVisitEvaluationResponse>,
)
