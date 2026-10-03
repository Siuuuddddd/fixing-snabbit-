package com.snabbit.prototype.notification.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterDeviceRequest(
    @field:NotBlank @field:Size(min = 20, max = 512)
    val pushToken: String,
)
