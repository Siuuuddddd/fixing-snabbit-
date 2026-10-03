package com.snabbit.prototype.support.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendSupportMessageRequest(
    @field:NotBlank @field:Size(max = 2_000)
    val body: String,
)
