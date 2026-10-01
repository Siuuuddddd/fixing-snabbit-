package com.snabbit.prototype.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class IntegrationProperties(
    val userServiceUrl: String,
    val workerServiceUrl: String,
    val walletServiceUrl: String,
)
