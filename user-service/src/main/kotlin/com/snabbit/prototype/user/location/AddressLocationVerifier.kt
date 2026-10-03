package com.snabbit.prototype.user.location

import com.snabbit.prototype.user.dto.response.GeocodedAddress
import com.snabbit.prototype.user.exception.LocationVerificationRejected
import java.math.BigDecimal
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Component

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
