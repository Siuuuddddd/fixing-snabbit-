package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmailRegisterRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 254) val email: String,
    @field:NotBlank val password: String,
    val termsAccepted: Boolean,
    @field:Size(max = 255) val deviceId: String? = null,
    @field:Size(max = 255) val deviceName: String? = null,
)
