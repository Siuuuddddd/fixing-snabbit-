package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy

data class MatchAndHoldRequest(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long? = null,
    val addressId: UUID,
    val startsAt: List<Instant> = emptyList(),
    val visits: List<RequestedVisit> = emptyList(),
    val expertPreference: ExpertPreference = ExpertPreference.ANY,
    val favouriteWorkerId: UUID? = null,
    val fallbackPolicy: FallbackPolicy = FallbackPolicy.AUTO_ASSIGN,
)
