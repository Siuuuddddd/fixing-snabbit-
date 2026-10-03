package com.snabbit.prototype.support.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class SupportIntegrationProperties(val bookingServiceUrl: String)
