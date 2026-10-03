package com.snabbit.prototype.booking.dto.response

import java.time.Instant

data class VisitAvailabilitySnapshot(
    val startsAt: Instant,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long?,
)
