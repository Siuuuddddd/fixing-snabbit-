package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class CreateBookingCommand(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val duration: Duration,
    val visits: List<VisitCommand>,
    val requirements: BookingRequirements = BookingRequirements(),
    val quotedTotalMinor: Long = 0,
    val serviceLatitude: BigDecimal? = null,
    val serviceLongitude: BigDecimal? = null,
    val bookingId: UUID = UUID.randomUUID(),
    val grossTotalMinor: Long = quotedTotalMinor,
    val plusDiscountMinor: Long = 0,
    val plusPassId: UUID? = null,
)

data class VisitCommand(
    val workerId: UUID,
    val startsAt: Instant,
)

data class BookingSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val grossTotalMinor: Long,
    val plusDiscountMinor: Long,
    val plusPassId: UUID?,
    val requirements: BookingRequirements,
    val visits: List<VisitSnapshot>,
)

data class PaymentQuoteSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val grossTotalMinor: Long,
    val plusDiscountMinor: Long,
    val currency: String,
    val holdExpiresAt: Instant,
)

data class WorkerLocationCommand(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
)

data class RatingContextSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<RatingVisitSnapshot>,
)

data class BookingOwnershipSnapshot(
    val bookingId: UUID,
    val customerId: UUID,
)

data class PlusPassBookingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val plusPassId: UUID?,
    val holdExpiresAt: Instant,
    val confirmedAt: Instant?,
)

data class RatingVisitSnapshot(
    val visitIndex: Int,
    val workerId: UUID,
    val completed: Boolean,
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
    val jobStatus: String?,
    val arrivedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
)
