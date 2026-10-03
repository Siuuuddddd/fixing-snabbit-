package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

data class SaveAddressRequest(
    @field:Size(min = 1, max = 60) val label: String,
    @field:Size(min = 5, max = 300) val addressLine: String,
    @field:Size(min = 1, max = 80) val city: String,
    @field:Pattern(regexp = "^400[0-9]{3}$") val pincode: String,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: BigDecimal,
    @field:Positive val locationAccuracyMeters: BigDecimal,
    val locationCapturedAt: Instant,
)
