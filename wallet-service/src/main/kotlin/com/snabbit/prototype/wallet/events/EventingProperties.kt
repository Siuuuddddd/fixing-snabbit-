package com.snabbit.prototype.wallet.events

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("eventing")
data class EventingProperties(
    val outboxTopic: String,
    val consumerTopic: String,
    val consumerGroup: String,
    val outboxPollDelay: Duration,
    val outboxClaimDuration: Duration,
    val outboxBatchSize: Int,
)
