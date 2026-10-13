package com.snabbit.prototype.worker.dto.response

import java.math.BigDecimal
import java.util.UUID

data class PublicWorkerProfile(
    val workerId: UUID,
    val firstName: String,
    val tier: String,
    val averageRating: BigDecimal,
    val ratingCount: Int,
    val completedJobs: Long,
    val tenureMonths: Long,
    val rankScore: BigDecimal,
    val customerRating: Int? = null,
)
