package com.snabbit.prototype.user.events

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("user-eventing")
data class UserEventingProperties(
    val topic: String,
    val pollDelay: Duration,
    val claimDuration: Duration,
    val batchSize: Int,
)
