package com.snabbit.prototype.notification.service

import com.snabbit.prototype.notification.domain.DevicePlatform
import com.snabbit.prototype.notification.domain.DeviceRegistration
import com.snabbit.prototype.notification.domain.DeliveryChannel
import com.snabbit.prototype.notification.domain.DeliveryStatus
import com.snabbit.prototype.notification.domain.Notification
import com.snabbit.prototype.notification.domain.NotificationDelivery
import com.snabbit.prototype.notification.domain.RecipientType
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.repository.NotificationDeliveryRepository
import com.snabbit.prototype.notification.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ConfigurationProperties("notifications")
data class NotificationProperties(
    val consumerGroup: String,
    val bookingTopic: String,
    val walletTopic: String,
    val streamPollDelay: Duration,
    val deliveryPollDelay: Duration,
    val deliveryClaimDuration: Duration,
    val deliveryBatchSize: Int,
    val deliveryMaxAttempts: Int,
)

data class NotificationSnapshot(
    val notificationId: Long,
    val eventType: String,
    val title: String,
    val body: String,
    val critical: Boolean,
    val createdAt: Instant,
    val readAt: Instant?,
)

data class DeviceSnapshot(
    val deviceId: UUID,
    val platform: String,
    val active: Boolean,
    val updatedAt: Instant,
)

data class NotificationTemplate(
    val title: String,
    val body: String,
    val critical: Boolean,
)

class NotificationNotFound(message: String) : RuntimeException(message)
class NotificationAccessDenied : RuntimeException("Authenticated recipient does not own this notification resource")
class NotificationRequestRejected(message: String) : RuntimeException(message)

@Service
class NotificationTransaction(
    private val notifications: NotificationRepository,
    private val deliveries: NotificationDeliveryRepository,
    private val devices: DeviceRegistrationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun record(
        eventId: UUID,
        recipientType: RecipientType,
        recipientId: UUID,
        eventType: String,
        template: NotificationTemplate,
        payload: String,
    ): NotificationSnapshot {
        notifications.findByEventId(eventId)?.let { return snapshot(it) }
        val now = clock.instant()
        val notification = notifications.save(
            Notification(
                eventId = eventId,
                recipientType = recipientType,
                recipientId = recipientId,
                eventType = eventType,
                title = template.title,
                body = template.body,
                critical = template.critical,
                payload = payload,
                createdAt = now,
            ),
        )
        val channels = buildList {
            add(DeliveryChannel.PUSH)
            if (template.critical) add(DeliveryChannel.SMS)
        }
        deliveries.saveAll(
            channels.map { channel ->
                NotificationDelivery(
                    UUID.randomUUID(), notification.id, channel, DeliveryStatus.PENDING, 0, now,
                )
            },
        )
        return snapshot(notification)
    }

    @Transactional(readOnly = true)
    fun history(recipientType: RecipientType, recipientId: UUID, afterId: Long): List<NotificationSnapshot> =
        notifications.findTop100ByRecipientTypeAndRecipientIdAndIdGreaterThanOrderByIdAsc(
            recipientType,
            recipientId,
            afterId.coerceAtLeast(0),
        )
            .map(::snapshot)

    @Transactional
    fun markRead(recipientType: RecipientType, recipientId: UUID, notificationId: Long): NotificationSnapshot {
        val notification = notifications.findForUpdate(notificationId)
            ?: throw NotificationNotFound("Notification was not found")
        if (notification.recipientType != recipientType || notification.recipientId != recipientId) {
            throw NotificationAccessDenied()
        }
        notification.markRead(clock.instant())
        return snapshot(notification)
    }

    @Transactional
    fun registerDevice(recipientType: RecipientType, recipientId: UUID, rawToken: String): DeviceSnapshot {
        val token = rawToken.trim()
        if (token.length !in 20..512) throw NotificationRequestRejected("Push token must contain 20 to 512 characters")
        val now = clock.instant()
        val device = devices.findByTokenForUpdate(token)?.apply { activateFor(recipientType, recipientId, now) }
            ?: devices.save(
                DeviceRegistration(
                    UUID.randomUUID(), recipientType, recipientId, DevicePlatform.ANDROID, token, true, now, now,
                ),
            )
        return deviceSnapshot(device)
    }

    @Transactional
    fun unregisterDevice(recipientType: RecipientType, recipientId: UUID, deviceId: UUID): DeviceSnapshot {
        val device = devices.findForUpdate(deviceId) ?: throw NotificationNotFound("Device registration was not found")
        if (device.recipientType != recipientType || device.recipientId != recipientId) {
            throw NotificationAccessDenied()
        }
        device.deactivate(clock.instant())
        return deviceSnapshot(device)
    }

    private fun snapshot(notification: Notification) = NotificationSnapshot(
        notification.id,
        notification.eventType,
        notification.title,
        notification.body,
        notification.critical,
        notification.createdAt,
        notification.readAt,
    )

    private fun deviceSnapshot(device: DeviceRegistration) = DeviceSnapshot(
        device.id, device.platform.name, device.active, device.updatedAt,
    )
}

@Service
class NotificationService(
    private val transactions: NotificationTransaction,
    private val properties: NotificationProperties,
) {
    fun history(recipientType: RecipientType, recipientId: UUID, afterId: Long) =
        transactions.history(recipientType, recipientId, afterId)
    fun markRead(recipientType: RecipientType, recipientId: UUID, notificationId: Long) =
        transactions.markRead(recipientType, recipientId, notificationId)
    fun registerDevice(recipientType: RecipientType, recipientId: UUID, token: String) =
        transactions.registerDevice(recipientType, recipientId, token)
    fun unregisterDevice(recipientType: RecipientType, recipientId: UUID, deviceId: UUID) =
        transactions.unregisterDevice(recipientType, recipientId, deviceId)

    fun stream(recipientType: RecipientType, recipientId: UUID, afterId: Long): Flow<NotificationSnapshot> = flow {
        var cursor = afterId.coerceAtLeast(0)
        while (currentCoroutineContext().isActive) {
            val batch = withContext(Dispatchers.IO) { transactions.history(recipientType, recipientId, cursor) }
            batch.forEach { notification ->
                emit(notification)
                cursor = notification.notificationId
            }
            delay(properties.streamPollDelay.toMillis())
        }
    }
}
