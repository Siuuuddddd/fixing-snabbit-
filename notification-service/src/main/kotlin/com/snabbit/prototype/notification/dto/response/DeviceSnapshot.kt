package com.snabbit.prototype.notification.dto.response

import java.time.Instant
import java.util.UUID

data class DeviceSnapshot(
    val deviceId: UUID,
    val platform: String,
    val active: Boolean,
    val updatedAt: Instant,
)
