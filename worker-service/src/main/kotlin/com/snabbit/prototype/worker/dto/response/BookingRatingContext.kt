package com.snabbit.prototype.worker.dto.response

import java.util.UUID

data class BookingRatingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<BookingRatingVisit>,
)
