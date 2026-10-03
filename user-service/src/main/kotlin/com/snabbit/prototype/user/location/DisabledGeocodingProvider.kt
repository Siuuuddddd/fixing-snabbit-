package com.snabbit.prototype.user.location

import com.snabbit.prototype.user.dto.response.GeocodedAddress
import java.math.BigDecimal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "geocoding", name = ["provider"], havingValue = "disabled", matchIfMissing = true)
class DisabledGeocodingProvider : GeocodingProvider {
    override fun geocode(addressLine: String, city: String, pincode: String): GeocodedAddress? = null
    override fun reverseGeocode(latitude: BigDecimal, longitude: BigDecimal): GeocodedAddress? = null
}
