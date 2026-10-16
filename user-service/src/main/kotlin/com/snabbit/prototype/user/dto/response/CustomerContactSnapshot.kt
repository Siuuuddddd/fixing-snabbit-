package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class CustomerContactSnapshot(
    val userId: UUID,
    val phoneNumber: String?,
    val displayName: String,
    val phoneVerified: Boolean = false,
)
