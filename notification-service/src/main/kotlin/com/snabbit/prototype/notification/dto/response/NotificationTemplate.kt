package com.snabbit.prototype.notification.dto.response

data class NotificationTemplate(
    val title: String,
    val body: String,
    val critical: Boolean,
)
