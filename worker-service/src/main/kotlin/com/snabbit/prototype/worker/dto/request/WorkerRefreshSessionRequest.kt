package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.NotBlank

data class WorkerRefreshSessionRequest(@field:NotBlank val refreshToken: String)
