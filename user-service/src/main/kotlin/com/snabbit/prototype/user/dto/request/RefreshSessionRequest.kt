package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank

data class RefreshSessionRequest(@field:NotBlank val refreshToken: String)
