package com.snabbit.prototype.wallet.eventing

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("eventing")
data class EventingProperties(
    val outboxTopic: String,
    val consumerTopic: String,
    val consumerGroup: String,
    val outboxPollDelay: Duration,
    val outboxClaimDuration: Duration,
    val outboxBatchSize: Int,
)
