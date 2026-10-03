package com.snabbit.prototype.user.location

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("geocoding")
data class GeocodingProperties(
    val provider: String = "disabled",
    val baseUrl: String = "https://maps.googleapis.com",
    val apiKey: String = "",
    val maximumMismatchMeters: Double = 250.0,
)
