package com.snabbit.prototype.user.events

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("user-eventing")
data class UserEventingProperties(
    val topic: String,
    val pollDelay: Duration,
    val claimDuration: Duration,
    val batchSize: Int,
)
