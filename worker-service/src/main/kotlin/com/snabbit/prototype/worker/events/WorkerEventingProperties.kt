package com.snabbit.prototype.worker.events

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("worker-eventing")
data class WorkerEventingProperties(
    val topic: String,
    val pollDelay: Duration,
    val claimDuration: Duration,
    val batchSize: Int,
)
