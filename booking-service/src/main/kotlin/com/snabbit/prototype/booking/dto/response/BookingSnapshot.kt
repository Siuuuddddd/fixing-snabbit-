package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Instant
import java.util.UUID

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
    val cancellationType: String? = null,
    val cancellationReason: String? = null,
    val cancellationFeeMinor: Long? = null,
    val refundAmountMinor: Long? = null,
    val cancelledAt: Instant? = null,
)
