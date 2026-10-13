package com.snabbit.prototype.user.rating

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class UserIntegrationProperties(
    val bookingServiceUrl: String,
    val walletServiceUrl: String = "http://localhost:8084",
    val workerServiceUrl: String = "http://localhost:8083",
)
