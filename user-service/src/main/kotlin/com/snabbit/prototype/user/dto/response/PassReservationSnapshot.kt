package com.snabbit.prototype.user.dto.response

import java.time.Instant
import java.util.UUID

data class PassReservationSnapshot(
    val passId: UUID?,
    val bookingId: UUID,
    val grossTotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val expiresAt: Instant?,
)
