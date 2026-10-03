package com.snabbit.prototype.support.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class OpenSupportTicketRequest(
    val bookingId: UUID,
    @field:NotBlank @field:Size(max = 160)
    val subject: String,
    @field:NotBlank @field:Size(max = 2_000)
    val initialMessage: String,
)
