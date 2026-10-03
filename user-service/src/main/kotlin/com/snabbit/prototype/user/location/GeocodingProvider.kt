package com.snabbit.prototype.user.location

import com.snabbit.prototype.user.dto.response.GeocodedAddress
import java.math.BigDecimal

interface GeocodingProvider {
    fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress?
    fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress?
}
