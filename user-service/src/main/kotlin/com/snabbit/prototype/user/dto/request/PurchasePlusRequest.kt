package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class PurchasePlusRequest(val walletId: UUID, @field:NotBlank @field:Size(max = 60) val planCode: String)
