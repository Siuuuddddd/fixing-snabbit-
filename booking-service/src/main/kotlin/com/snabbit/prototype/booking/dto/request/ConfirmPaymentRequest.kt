package com.snabbit.prototype.booking.dto.request

import jakarta.validation.constraints.NotBlank

data class ConfirmPaymentRequest(
    @field:NotBlank
    val paymentReference: String,
)
