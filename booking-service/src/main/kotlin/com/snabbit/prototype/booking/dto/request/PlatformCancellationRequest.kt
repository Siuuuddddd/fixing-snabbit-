package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PlatformCancellationRequest(
    @field:NotBlank @field:Size(max = 240)
    val reason: String,
)
