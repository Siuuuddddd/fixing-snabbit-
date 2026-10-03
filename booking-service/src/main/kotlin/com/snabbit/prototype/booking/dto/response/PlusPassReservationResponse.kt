package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PlusPassReservationResponse(
    val passId: UUID?,
    val bookingId: UUID,
    val grossTotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val expiresAt: Instant?,
)
