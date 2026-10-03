package com.snabbit.prototype.notification.dto.response

import java.time.Instant

data class NotificationSnapshot(
    val notificationId: Long,
    val eventType: String,
    val title: String,
    val body: String,
    val critical: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
)
