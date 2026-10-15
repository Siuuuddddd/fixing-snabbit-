package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmailLoginRequest(
    @field:NotBlank @field:Size(max = 254) val email: String,
    @field:NotBlank val password: String,
    @field:Size(max = 255) val deviceId: String? = null,
    @field:Size(max = 255) val deviceName: String? = null,
)
