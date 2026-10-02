package com.snabbit.prototype.notification.eventing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.application.NotificationProperties
import com.snabbit.prototype.notification.application.NotificationTemplate
import com.snabbit.prototype.notification.application.NotificationTransaction
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
import org.springframework.web.client.RestClient
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class EventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredAt: Instant,
    val payload: JsonNode,
)

@ConfigurationProperties("integrations")
data class NotificationIntegrationProperties(
    val bookingServiceUrl: String,
    val walletServiceUrl: String,
    val userServiceUrl: String,
)

data class BookingOwner(val bookingId: UUID, val customerId: UUID)
data class WalletOwner(val walletId: UUID, val customerId: UUID)

@Component
class EventRecipientResolver(builder: RestClient.Builder, properties: NotificationIntegrationProperties) {
    private val bookingClient = builder.clone().baseUrl(properties.bookingServiceUrl).build()
    private val walletClient = builder.clone().baseUrl(properties.walletServiceUrl).build()

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

@Component
class NotificationTemplates {
    fun forEvent(event: EventEnvelope): NotificationTemplate? = when (event.eventType) {
        "BookingHeld" -> NotificationTemplate("Slot held", "Your service slot is held while a worker responds.", false)
        "BookingAwaitingPayment" -> NotificationTemplate("Worker accepted", "Complete payment before the slot hold expires.", true)
        "WorkerOfferRejected", "WorkerOfferExpired" ->
            NotificationTemplate("Worker unavailable", "We could not confirm this worker. Your held slot was released.", true)
        "BookingConfirmed" -> NotificationTemplate("Booking confirmed", "Payment is verified and your worker is assigned.", true)
        "WorkerArrived" -> NotificationTemplate("Worker arrived", "Your worker has arrived at the service location.", true)
        "ServiceStarted" -> NotificationTemplate("Service started", "Your booked home service is now in progress.", false)
        "ServiceCompleted", "BookingCompleted" ->
            NotificationTemplate("Service completed", "Your service is complete. You can now leave a rating.", false)
        "BookingExpired" -> NotificationTemplate("Booking expired", "The slot expired before confirmation. Any payment will be reversed.", true)
        "BookingCancelled" -> NotificationTemplate("Booking cancelled", "Your unconfirmed booking was cancelled.", false)
        "BookingCustomerCancelled" -> NotificationTemplate("Cancellation accepted", "Your refund is being returned after the cancellation fee.", true)
        "BookingPlatformCancelled" -> NotificationTemplate("Full refund started", "We could not provide the service. A full refund is being returned.", true)
        "WalletTopUpSucceeded" -> NotificationTemplate("Money added", "Your wallet top-up was verified and credited.", true)
        "WalletPaymentSucceeded" -> NotificationTemplate("Payment received", "Your booking payment was verified.", false)
        "WalletPaymentFailed" -> NotificationTemplate("Payment stopped", "The booking payment did not complete. Reserved wallet funds were released.", true)
        "WalletPaymentRefundRequired", "WalletRefundQueued" ->
            NotificationTemplate("Refund started", "Your refund has been queued securely.", true)
        "WalletRefundCompleted" -> NotificationTemplate("Refund completed", "Your booking refund has completed.", true)
        else -> null
    }
}

@Repository
class ProcessedEventStore(private val jdbc: JdbcTemplate) {
    fun claim(consumer: String, eventId: UUID, now: Instant): Boolean = jdbc.update(
        """
        INSERT INTO processed_message (consumer_name, message_id, processed_at)
        VALUES (?, ?, ?)
        ON CONFLICT (consumer_name, message_id) DO NOTHING
        """.trimIndent(),
        consumer, eventId, Timestamp.from(now),
    ) == 1
}

@Service
class NotificationEventHandler(
    private val processed: ProcessedEventStore,
    private val transactions: NotificationTransaction,
    private val properties: NotificationProperties,
    private val mapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun handle(event: EventEnvelope, customerId: UUID, template: NotificationTemplate) {
        if (!processed.claim(properties.consumerGroup, event.eventId, clock.instant())) return
        transactions.record(event.eventId, customerId, event.eventType, template, mapper.writeValueAsString(event.payload))
    }
}

@Component
class NotificationEventListener(
    private val mapper: ObjectMapper,
    private val resolver: EventRecipientResolver,
    private val templates: NotificationTemplates,
    private val handler: NotificationEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(
        topics = ["\${notifications.booking-topic}", "\${notifications.wallet-topic}"],
        groupId = "\${notifications.consumer-group}",
    )
    fun consume(rawEvent: String) {
        val event = mapper.readValue(rawEvent, EventEnvelope::class.java)
        val template = templates.forEvent(event) ?: return
        handler.handle(event, resolver.customerId(event), template)
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Notification event moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
