package com.snabbit.prototype.user.location

import com.snabbit.prototype.user.dto.response.GeocodedAddress

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

interface GeocodingProvider {
    fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress?
    fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress?
}
