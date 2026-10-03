package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.util.UUID

data class PlusPassReservationResponse(
    val passId: UUID?,
    val bookingId: UUID,
    val grossTotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val expiresAt: Instant?,
)
