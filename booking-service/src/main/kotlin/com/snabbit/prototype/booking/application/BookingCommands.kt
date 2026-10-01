package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class CreateBookingCommand(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val duration: Duration,
    val visits: List<VisitCommand>,
    val requirements: BookingRequirements = BookingRequirements(),
    val quotedTotalMinor: Long = 0,
)

data class VisitCommand(
    val workerId: UUID,
    val startsAt: Instant,
)

data class BookingSnapshot(
    val bookingId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val requirements: BookingRequirements,
    val visits: List<VisitSnapshot>,
)

data class VisitSnapshot(
    val visitIndex: Int,
    val workerId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val holdExpiresAt: Instant,
    val offerId: UUID,
    val offerStatus: String,
    val offerExpiresAt: Instant,
)
