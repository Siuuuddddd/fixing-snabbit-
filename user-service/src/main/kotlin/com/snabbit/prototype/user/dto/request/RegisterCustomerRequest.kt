package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterCustomerRequest(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String,
    @field:NotBlank @field:Size(min = 2, max = 60) val displayName: String,
    @field:NotBlank val registrationToken: String,
)
