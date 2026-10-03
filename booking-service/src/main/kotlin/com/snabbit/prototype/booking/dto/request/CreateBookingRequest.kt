package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.enums.BookingType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.Valid
import java.util.UUID

data class CreateBookingRequest(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
    @field:NotEmpty
    val visits: List<@Valid VisitRequest>,
    val requirements: BookingRequirements = BookingRequirements(),
)
