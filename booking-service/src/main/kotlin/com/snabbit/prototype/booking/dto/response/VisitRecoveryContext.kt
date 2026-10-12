package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class VisitRecoveryContext(
    val bookingId: UUID,
    val customerId: UUID,
    val serviceId: UUID,
    val visitIndex: Int,
    val failedWorkerId: UUID,
    val startsAt: Instant,
    val durationMinutes: Int,
    val quotedPriceMinor: Long,
    val assignedWorkerTier: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val requirements: BookingRequirements,
    val expertPreference: ExpertPreference,
    val favouriteWorkerId: UUID?,
    val fallbackPolicy: FallbackPolicy,
)
