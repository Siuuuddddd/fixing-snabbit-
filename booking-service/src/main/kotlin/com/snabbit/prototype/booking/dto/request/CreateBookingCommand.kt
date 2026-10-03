package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.dto.request.VisitCommand

import com.snabbit.prototype.booking.domain.enums.BookingType

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
    val serviceAddressId: UUID? = null,
    val serviceAddressLabel: String? = null,
    val serviceAddressLine: String? = null,
    val serviceAddressPincode: String? = null,
    val idempotencyKey: String? = null,
    val requestFingerprint: String? = null,
)
