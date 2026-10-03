package com.snabbit.prototype.booking.dto.request

import java.time.Instant
import java.util.UUID

data class ReservePlusPassRequest(
    val bookingId: UUID,
    val serviceCode: String,
    val grossTotalMinor: Long,
    val holdUntil: Instant,
)
