package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.dto.response.ClaimedDelivery

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.snabbit.prototype.platform.SmsGateway
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.events.NotificationIntegrationProperties
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
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
