package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank

data class SaveCustomerPhoneRequest(
    @field:NotBlank val phoneNumber: String,
)
