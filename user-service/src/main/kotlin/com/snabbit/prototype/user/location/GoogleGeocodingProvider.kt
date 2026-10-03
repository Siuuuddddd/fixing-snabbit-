package com.snabbit.prototype.user.location

import com.fasterxml.jackson.databind.JsonNode
import com.snabbit.prototype.user.dto.response.GeocodedAddress
import java.math.BigDecimal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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
