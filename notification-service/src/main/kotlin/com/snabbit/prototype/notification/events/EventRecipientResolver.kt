package com.snabbit.prototype.notification.events

import com.snabbit.prototype.notification.dto.response.WalletOwner

import com.snabbit.prototype.notification.dto.response.BookingOwner

import com.snabbit.prototype.notification.dto.response.NotificationTemplate

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.service.NotificationTransaction
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class EventRecipientResolver(factory: InternalServiceClientFactory, properties: NotificationIntegrationProperties) {
    private val bookingClient = factory.create(properties.bookingServiceUrl)
    private val walletClient = factory.create(properties.walletServiceUrl)

    fun customerId(event: EventEnvelope): UUID {
        val bookingId = event.payload.path("bookingId").takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.takeIf(String::isNotBlank)?.let(UUID::fromString)
        if (bookingId != null) {
            return bookingClient.get()
                .uri("/internal/v1/bookings/{bookingId}/ownership", bookingId)
                .retrieve()
                .body(BookingOwner::class.java)
                ?.customerId
                ?: error("Booking Service returned an empty owner")
        }
        val walletId = event.payload.path("walletId").takeUnless { it.isMissingNode || it.isNull }
            ?.asText()?.takeIf(String::isNotBlank)?.let(UUID::fromString)
            ?: throw IllegalArgumentException("Notification event has no bookingId or walletId")
        return walletClient.get()
            .uri("/internal/v1/wallets/{walletId}/ownership", walletId)
            .retrieve()
            .body(WalletOwner::class.java)
            ?.customerId
            ?: error("Wallet Service returned an empty owner")
    }
}
