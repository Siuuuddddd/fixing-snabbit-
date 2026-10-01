package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.domain.BookingType
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class HoldSlotCommand(
    val bookingId: UUID,
    val customerId: UUID,
    val workerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val startsAt: Instant,
    val duration: Duration,
)
