package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class MatchAndHoldCommand(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val duration: Duration,
    val addressId: UUID,
    val startsAt: List<Instant>,
    val idempotencyKey: String? = null,
)
