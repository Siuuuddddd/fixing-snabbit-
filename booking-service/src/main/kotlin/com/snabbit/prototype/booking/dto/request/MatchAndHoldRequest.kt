package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class MatchAndHoldRequest(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
    val addressId: UUID,
    @field:NotEmpty @field:Size(max = 7)
    val startsAt: List<Instant>,
)
