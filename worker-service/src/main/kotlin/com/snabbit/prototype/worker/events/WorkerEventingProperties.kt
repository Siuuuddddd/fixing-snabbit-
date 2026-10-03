package com.snabbit.prototype.worker.events

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("worker-eventing")
data class WorkerEventingProperties(
    val topic: String,
    val pollDelay: Duration,
    val claimDuration: Duration,
    val batchSize: Int,
)
