package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class ServiceOfferingSnapshot(
    val serviceId: UUID,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)
