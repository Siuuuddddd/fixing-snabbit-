package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class WorkerArrivalRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: BigDecimal,
    @field:Positive
    val accuracyMeters: BigDecimal,
)
