package com.snabbit.prototype.notification.events

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class NotificationIntegrationProperties(
    val bookingServiceUrl: String,
    val walletServiceUrl: String,
    val userServiceUrl: String,
    val workerServiceUrl: String,
)
