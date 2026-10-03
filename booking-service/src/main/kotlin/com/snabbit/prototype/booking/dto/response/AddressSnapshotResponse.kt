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

data class AddressSnapshotResponse(
    val addressId: UUID,
    val userId: UUID,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val verified: Boolean,
)
