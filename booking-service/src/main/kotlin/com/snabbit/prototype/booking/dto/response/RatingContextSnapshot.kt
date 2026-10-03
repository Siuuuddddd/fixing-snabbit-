package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class RatingContextSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<RatingVisitSnapshot>,
)
