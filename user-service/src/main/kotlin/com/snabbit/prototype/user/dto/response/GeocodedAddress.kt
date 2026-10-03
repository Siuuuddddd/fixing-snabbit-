package com.snabbit.prototype.user.dto.response

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeocodedAddress(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val formattedAddress: String,
    val city: String?,
    val pincode: String?,
)
