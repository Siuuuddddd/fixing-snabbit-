package com.snabbit.prototype.wallet.booking

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("integrations")
data class BookingIntegrationProperties(val bookingServiceUrl: String)
