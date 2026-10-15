package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank val newPassword: String,
)
