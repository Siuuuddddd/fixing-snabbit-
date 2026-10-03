package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class BookingRatingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<BookingRatingVisit>,
)
