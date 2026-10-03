package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Instant
import java.util.UUID

data class WorkerOfferFeedItem(
    val offerId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val serviceId: UUID,
    val serviceCode: String,
    val serviceName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int,
    val offerExpiresAt: Instant,
    val requirements: BookingRequirements,
)
