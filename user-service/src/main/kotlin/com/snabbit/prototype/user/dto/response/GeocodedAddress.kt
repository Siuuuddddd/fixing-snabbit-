package com.snabbit.prototype.user.dto.response

import java.math.BigDecimal

data class GeocodedAddress(
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val formattedAddress: String,
    val city: String?,
    val pincode: String?,
)
