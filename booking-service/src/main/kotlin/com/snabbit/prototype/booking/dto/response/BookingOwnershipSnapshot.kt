package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class BookingOwnershipSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
)
