package com.snabbit.prototype.booking.dto.response

data class TierAvailabilitySnapshot(
    val tier: String,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long?,
)
