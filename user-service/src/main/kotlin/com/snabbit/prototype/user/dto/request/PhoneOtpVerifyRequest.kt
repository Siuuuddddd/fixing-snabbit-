package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PhoneOtpVerifyRequest(
    @field:NotBlank @field:Size(max = 16) val phoneNumber: String,
    @field:Pattern(regexp = "^[0-9]{6}$") val code: String,
    @field:Size(max = 120) val name: String? = null,
    @field:Size(max = 255) val deviceId: String? = null,
    @field:Size(max = 255) val deviceName: String? = null,
    val rememberMe: Boolean = true,
)
