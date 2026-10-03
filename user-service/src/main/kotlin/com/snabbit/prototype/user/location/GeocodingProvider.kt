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

data class GeocodedAddress(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val formattedAddress: String,
    val city: String?,
    val pincode: String?,
)

interface GeocodingProvider {
    fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress?
    fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress?
}

@ConfigurationProperties("geocoding")
data class GeocodingProperties(
    val provider: String = "disabled",
    val baseUrl: String = "https://maps.googleapis.com",
    val apiKey: String = "",
    val maximumMismatchMeters: Double = 250.0,
)

@Component
@ConditionalOnProperty(prefix = "geocoding", name = ["provider"], havingValue = "disabled", matchIfMissing = true)
class DisabledGeocodingProvider : GeocodingProvider {
    override fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress? = null
    override fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress? = null
}

@Component
@ConditionalOnProperty(prefix = "geocoding", name = ["provider"], havingValue = "google")
class GoogleGeocodingProvider(
    builder: RestClient.Builder,
    private val properties: GeocodingProperties,
) : GeocodingProvider {
    private val client = builder.baseUrl(properties.baseUrl).build()

    init {
        require(properties.apiKey.isNotBlank()) { "Google Geocoding API key is required" }
    }

    override fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress? =
        request(mapOf("address" to "$addressLine, $city $pincode, India"))

    override fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress? =
        request(mapOf("latlng" to "$latitude,$longitude"))

    private fun request(query: Map<String, String>): GeocodedAddress? {
        val response = client.get()
            .uri { builder ->
                var uri = builder.path("/maps/api/geocode/json")
                query.forEach { (key, value) -> uri = uri.queryParam(key, value) }
                uri.queryParam("key", properties.apiKey).build()
            }
            .retrieve()
            .body(JsonNode::class.java)
            ?: error("Google Geocoding returned an empty response")
        return when (val status = response.path("status").asText()) {
            "OK" -> response.path("results").firstOrNull()?.toAddress()
                ?: error("Google Geocoding returned OK without a result")
            "ZERO_RESULTS" -> null
            else -> error("Google Geocoding failed with status $status")
        }
    }

    private fun JsonNode.toAddress(): GeocodedAddress {
        val location = path("geometry").path("location")
        val components = path("address_components")
        fun component(vararg types: String): String? = components.firstOrNull { component ->
            val found = component.path("types").map(JsonNode::asText).toSet()
            types.any(found::contains)
        }?.path("long_name")?.asText()
        return GeocodedAddress(
            location.path("lat").decimalValue(),
            location.path("lng").decimalValue(),
            path("formatted_address").asText(),
            component("locality", "administrative_area_level_2"),
            component("postal_code"),
        )
    }
}

@Component
class AddressLocationVerifier(
    private val provider: GeocodingProvider,
    private val properties: GeocodingProperties,
) {
    fun verify(
        addressLine: String,
        city: String,
        pincode: String,
        latitude: BigDecimal,
        longitude: BigDecimal,
    ) {
        val geocoded = provider.geocode(addressLine, city, pincode) ?: return
        val reversed = provider.reverseGeocode(latitude, longitude)
            ?: throw LocationVerificationRejected("The captured GPS location could not be resolved to an address")
        requireMumbai(geocoded, pincode)
        requireMumbai(reversed, pincode)
        val mismatch = distanceMeters(latitude, longitude, geocoded.latitude, geocoded.longitude)
        if (mismatch > properties.maximumMismatchMeters) {
            throw LocationVerificationRejected(
                "Captured GPS location is ${mismatch.toLong()} metres from the entered address",
            )
        }
    }

    private fun requireMumbai(address: GeocodedAddress, expectedPincode: String) {
        if (!address.city.orEmpty().contains("Mumbai", ignoreCase = true) || address.pincode != expectedPincode) {
            throw LocationVerificationRejected("The address provider could not confirm the Mumbai pincode")
        }
    }

    private fun distanceMeters(
        latitudeA: BigDecimal,
        longitudeA: BigDecimal,
        latitudeB: BigDecimal,
        longitudeB: BigDecimal,
    ): Double {
        val lat1 = Math.toRadians(latitudeA.toDouble())
        val lat2 = Math.toRadians(latitudeB.toDouble())
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(longitudeB.toDouble() - longitudeA.toDouble())
        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}

class LocationVerificationRejected(message: String) : RuntimeException(message)
