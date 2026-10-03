package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ReservePlusPassRequest(
    val bookingId: UUID,
    val serviceCode: String,
    val grossTotalMinor: Long,
    val holdUntil: Instant,
)
