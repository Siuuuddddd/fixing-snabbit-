package com.snabbit.prototype.user.dto.response

import java.time.Instant
import java.util.UUID

data class PlusStatusSnapshot(
    val userId: UUID,
    val active: Boolean,
    val expiresAt: Instant?,
    val availablePasses: Long,
)
