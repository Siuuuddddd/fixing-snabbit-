package com.snabbit.prototype.user.dto.response

import java.time.Instant
import java.util.UUID

data class PlusPassBookingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val plusPassId: UUID?,
    val holdExpiresAt: Instant,
    val confirmedAt: Instant?,
)
