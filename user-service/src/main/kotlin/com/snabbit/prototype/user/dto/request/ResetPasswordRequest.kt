package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank @field:Size(max = 254) val email: String,
    @field:Pattern(regexp = "^[0-9]{6}$") val code: String,
    @field:NotBlank val newPassword: String,
)
