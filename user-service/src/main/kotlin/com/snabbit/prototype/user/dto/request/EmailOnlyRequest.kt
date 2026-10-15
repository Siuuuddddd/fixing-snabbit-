package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmailOnlyRequest(@field:NotBlank @field:Size(max = 254) val email: String)
