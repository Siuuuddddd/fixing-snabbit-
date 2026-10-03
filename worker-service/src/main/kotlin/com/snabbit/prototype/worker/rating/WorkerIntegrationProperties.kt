package com.snabbit.prototype.worker.rating

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class WorkerIntegrationProperties(val bookingServiceUrl: String)
