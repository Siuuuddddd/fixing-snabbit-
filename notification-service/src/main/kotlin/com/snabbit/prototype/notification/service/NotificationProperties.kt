package com.snabbit.prototype.notification.service

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("notifications")
data class NotificationProperties(
    val consumerGroup: String,
    val bookingTopic: String,
    val walletTopic: String,
    val streamPollDelay: Duration,
    val deliveryPollDelay: Duration,
    val deliveryClaimDuration: Duration,
    val deliveryBatchSize: Int,
    val deliveryMaxAttempts: Int,
)
