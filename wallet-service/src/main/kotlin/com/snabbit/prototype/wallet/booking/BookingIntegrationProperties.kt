package com.snabbit.prototype.wallet.booking

import com.snabbit.prototype.wallet.support.BookingQuoteUnavailable
import com.snabbit.prototype.wallet.security.InternalServiceClientFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.util.UUID

@ConfigurationProperties("integrations")
data class BookingIntegrationProperties(val bookingServiceUrl: String)
