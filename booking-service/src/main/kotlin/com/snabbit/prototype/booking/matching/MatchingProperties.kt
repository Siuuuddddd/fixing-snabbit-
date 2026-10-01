package com.snabbit.prototype.booking.matching

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class MatchingProperties(
    val userServiceUrl: String,
    val workerServiceUrl: String,
)
