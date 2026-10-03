package com.snabbit.prototype.notification.delivery

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("notifications.push.fcm")
data class FcmProperties(
    val projectId: String,
    val serviceAccountFile: String,
    val baseUrl: String = "https://fcm.googleapis.com",
)
