package com.snabbit.prototype.worker.dto.response

import java.math.BigDecimal
import java.util.UUID

data class WorkerRatingSnapshot(
    val ratingId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val workerId: UUID,
    val customerId: UUID,
    val stars: Int,
    val comment: String?,
    val workerAverageRating: BigDecimal,
)
