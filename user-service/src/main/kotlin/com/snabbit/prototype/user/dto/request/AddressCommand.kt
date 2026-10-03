package com.snabbit.prototype.user.dto.request

import java.math.BigDecimal
import java.time.Instant

data class AddressCommand(
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val locationAccuracyMeters: BigDecimal,
    val locationCapturedAt: Instant,
)
