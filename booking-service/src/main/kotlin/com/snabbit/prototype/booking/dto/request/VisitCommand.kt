package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class VisitCommand(
    val workerId: UUID,
    val startsAt: Instant,
)
