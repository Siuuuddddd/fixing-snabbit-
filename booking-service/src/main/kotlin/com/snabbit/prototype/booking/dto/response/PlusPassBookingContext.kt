package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class PlusPassBookingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val plusPassId: UUID?,
    val holdExpiresAt: Instant,
    val confirmedAt: Instant?,
)
