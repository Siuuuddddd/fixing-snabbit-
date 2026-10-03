package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.BookingRequirements
import java.time.Duration
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

data class WorkerLocationCommand(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal,
)
