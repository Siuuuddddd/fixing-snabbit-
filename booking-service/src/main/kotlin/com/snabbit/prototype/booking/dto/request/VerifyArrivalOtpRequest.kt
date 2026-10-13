package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.Pattern

data class VerifyArrivalOtpRequest(
    @field:Pattern(regexp = "^[0-9]{6}$", message = "Arrival OTP must be a 6-digit code")
    val code: String,
)
