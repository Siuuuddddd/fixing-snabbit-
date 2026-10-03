package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class BookingAvailabilitySnapshot(
    val serviceId: UUID,
    val durationMinutes: Int,
    val allVisitsAvailable: Boolean,
    val sameWorkerAvailableForAllVisits: Boolean,
    val visits: List<VisitAvailabilitySnapshot>,
)
