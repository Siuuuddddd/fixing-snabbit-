package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class RatingVisitSnapshot(
    val visitIndex: Int,
    val workerId: UUID,
    val completed: Boolean,
)
