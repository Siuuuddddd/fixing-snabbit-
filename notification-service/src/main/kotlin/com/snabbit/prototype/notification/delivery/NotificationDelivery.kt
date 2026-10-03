package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.platform.SmsGateway
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.events.NotificationIntegrationProperties
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import com.snabbit.prototype.notification.domain.RecipientType
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ClaimedDelivery(
    val deliveryId: UUID,
    val notificationId: Long,
    val recipientType: RecipientType,
    val recipientId: UUID,
    val channel: String,
    val title: String,
    val body: String,
    val attempts: Int,
)

data class RecipientContact(val phoneNumber: String)

@Repository
class NotificationDeliveryStore(private val jdbc: JdbcTemplate) {
    fun claimBatch(claimId: UUID, now: Instant, claimUntil: Instant, limit: Int): List<ClaimedDelivery> =
        jdbc.query(
            """
            WITH candidates AS (
                SELECT delivery.id
                FROM notification_delivery delivery
                WHERE (
                    (delivery.status = 'PENDING' AND delivery.next_attempt_at <= ?)
                    OR (delivery.status = 'PROCESSING' AND delivery.claim_until < ?)
                )
                ORDER BY delivery.next_attempt_at, delivery.id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE notification_delivery delivery
            SET status = 'PROCESSING', claimed_by = ?, claim_until = ?, attempts = delivery.attempts + 1
            FROM candidates, notification notification
            WHERE delivery.id = candidates.id AND notification.id = delivery.notification_id
            RETURNING delivery.id, delivery.notification_id, notification.recipient_type, notification.recipient_id,
                      delivery.channel, notification.title, notification.body, delivery.attempts
            """.trimIndent(),
            { result, _ ->
                ClaimedDelivery(
                    UUID.fromString(result.getString("id")),
                    result.getLong("notification_id"),
                    RecipientType.valueOf(result.getString("recipient_type")),
                    UUID.fromString(result.getString("recipient_id")),
                    result.getString("channel"),
                    result.getString("title"),
                    result.getString("body"),
                    result.getInt("attempts"),
                )
            },
            Timestamp.from(now), Timestamp.from(now), limit, claimId, Timestamp.from(claimUntil),
        )

    fun markDelivered(deliveryId: UUID, claimId: UUID, now: Instant) {
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = 'DELIVERED', delivered_at = ?, claimed_by = NULL, claim_until = NULL, last_error = NULL
            WHERE id = ? AND claimed_by = ? AND status = 'PROCESSING'
            """.trimIndent(),
            Timestamp.from(now), deliveryId, claimId,
        )
    }

    fun releaseFailure(
        deliveryId: UUID,
        claimId: UUID,
        attempts: Int,
        maxAttempts: Int,
        now: Instant,
        error: String,
    ) {
        val dead = attempts >= maxAttempts
        val retryDelay = Duration.ofSeconds((30L shl (attempts - 1).coerceIn(0, 6)))
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = ?, next_attempt_at = ?, claimed_by = NULL, claim_until = NULL, last_error = ?
            WHERE id = ? AND claimed_by = ? AND status = 'PROCESSING'
            """.trimIndent(),
            if (dead) "DEAD" else "PENDING",
            Timestamp.from(now.plus(retryDelay)),
            error.take(500),
            deliveryId,
            claimId,
        )
    }
}

@Service
class NotificationDeliveryQuery(private val devices: DeviceRegistrationRepository) {
    @Transactional(readOnly = true)
    fun pushTokens(recipientType: RecipientType, recipientId: UUID): List<String> =
        devices.findAllByRecipientTypeAndRecipientIdAndActiveTrue(recipientType, recipientId).map { it.pushToken }
}

@Component
class NotificationContactClient(factory: InternalServiceClientFactory, properties: NotificationIntegrationProperties) {
    private val userClient = factory.create(properties.userServiceUrl)
    private val workerClient = factory.create(properties.workerServiceUrl)

    fun contact(recipientType: RecipientType, recipientId: UUID): RecipientContact = when (recipientType) {
        RecipientType.CUSTOMER -> userClient.get().uri("/internal/v1/users/{userId}/contact", recipientId)
        RecipientType.WORKER -> workerClient.get().uri("/internal/v1/workers/{workerId}/contact", recipientId)
    }
        .retrieve()
        .body(RecipientContact::class.java)
        ?: error("Recipient service returned an empty contact")
}

interface PushDeliveryAdapter {
    fun send(tokens: List<String>, title: String, body: String)
}

interface SmsDeliveryAdapter {
    fun send(phoneNumber: String, body: String)
}

@Component
@Profile("local", "test")
class LoggingPushDeliveryAdapter : PushDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(tokens: List<String>, title: String, body: String) {
        if (tokens.isEmpty()) error("Recipient has no active push device")
        logger.info("Development push delivered to {} device(s): {}", tokens.size, title)
    }
}

@Component
@Profile("local", "test")
class LoggingSmsDeliveryAdapter : SmsDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(phoneNumber: String, body: String) {
        logger.info("Development SMS delivered to ******{}", phoneNumber.takeLast(4))
    }
}

@Component
@Profile("!local & !test")
class TwilioSmsDeliveryAdapter(private val gateway: SmsGateway) : SmsDeliveryAdapter {
    override fun send(phoneNumber: String, body: String) = gateway.send(phoneNumber, body)
}

@Component
class NotificationDeliveryScheduler(
    private val store: NotificationDeliveryStore,
    private val query: NotificationDeliveryQuery,
    private val contacts: NotificationContactClient,
    private val push: PushDeliveryAdapter,
    private val sms: SmsDeliveryAdapter,
    private val properties: NotificationProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${notifications.delivery-poll-delay}")
    fun deliverPending() {
        val claimId = UUID.randomUUID()
        val now = clock.instant()
        store.claimBatch(
            claimId, now, now.plus(properties.deliveryClaimDuration), properties.deliveryBatchSize,
        ).forEach { delivery -> deliverOne(delivery, claimId) }
    }

    private fun deliverOne(delivery: ClaimedDelivery, claimId: UUID) {
        try {
            when (delivery.channel) {
                "PUSH" -> push.send(
                    query.pushTokens(delivery.recipientType, delivery.recipientId), delivery.title, delivery.body,
                )
                "SMS" -> sms.send(
                    contacts.contact(delivery.recipientType, delivery.recipientId).phoneNumber, delivery.body,
                )
                else -> error("Unsupported delivery channel ${delivery.channel}")
            }
            store.markDelivered(delivery.deliveryId, claimId, clock.instant())
        } catch (error: Exception) {
            logger.warn("Notification delivery {} failed", delivery.deliveryId, error)
            store.releaseFailure(
                delivery.deliveryId,
                claimId,
                delivery.attempts,
                properties.deliveryMaxAttempts,
                clock.instant(),
                error.message.orEmpty(),
            )
        }
    }
}
