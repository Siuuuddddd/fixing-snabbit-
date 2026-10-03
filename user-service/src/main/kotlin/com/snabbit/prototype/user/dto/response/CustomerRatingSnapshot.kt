package com.snabbit.prototype.user.dto.response

import java.math.BigDecimal
import java.util.UUID

data class CustomerRatingSnapshot(
    val ratingId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val customerId: UUID,
    val workerId: UUID,
    val stars: Int,
    val comment: String?,
    val customerAverageRating: BigDecimal,
)
