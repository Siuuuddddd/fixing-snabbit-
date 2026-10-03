package com.snabbit.prototype.wallet.dto.response

import com.snabbit.prototype.wallet.support.BookingQuoteUnavailable
import com.snabbit.prototype.wallet.security.InternalServiceClientFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.util.UUID

data class BookingPaymentQuote(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val currency: String,
    val holdExpiresAt: Instant,
)
