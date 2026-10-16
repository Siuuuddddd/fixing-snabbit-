package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PhoneOtpRequest(
    @field:NotBlank @field:Size(max = 16) val phoneNumber: String,
)
