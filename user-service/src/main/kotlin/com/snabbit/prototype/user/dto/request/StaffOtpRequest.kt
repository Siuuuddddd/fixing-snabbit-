package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.Pattern

data class StaffOtpRequest(@field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String)
