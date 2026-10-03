package com.snabbit.prototype.user.location

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

@ConfigurationProperties("geocoding")
data class GeocodingProperties(
    val provider: String = "disabled",
    val baseUrl: String = "https://maps.googleapis.com",
    val apiKey: String = "",
    val maximumMismatchMeters: Double = 250.0,
)
