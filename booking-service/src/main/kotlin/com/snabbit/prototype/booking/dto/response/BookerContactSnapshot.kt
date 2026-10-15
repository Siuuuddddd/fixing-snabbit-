package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class BookerContactSnapshot(
    val userId: UUID,
    val phoneNumber: String?,
    val displayName: String,
)
