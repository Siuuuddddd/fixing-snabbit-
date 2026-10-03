package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.Pattern

data class WorkerOtpRequest(@field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String)
