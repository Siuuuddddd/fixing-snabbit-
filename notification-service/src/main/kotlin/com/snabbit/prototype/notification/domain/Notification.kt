package com.snabbit.prototype.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class RecipientType { CUSTOMER, WORKER }

@Entity
@Table(name = "notification")
class Notification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "event_id", nullable = false, unique = true) val eventId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false) val recipientType: RecipientType,
    @Column(name = "recipient_id", nullable = false) val recipientId: UUID,
    @Column(name = "event_type", nullable = false) val eventType: String,
    @Column(nullable = false) val title: String,
    @Column(nullable = false) val body: String,
    @Column(nullable = false) val critical: Boolean,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") val payload: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "read_at") var readAt: Instant? = null,
) {
    fun markRead(now: Instant) {
        if (readAt == null) readAt = now
    }
}

enum class DevicePlatform { ANDROID }

@Entity
@Table(name = "device_registration")
class DeviceRegistration(
    @Id val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false) var recipientType: RecipientType,
    @Column(name = "recipient_id", nullable = false) var recipientId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) val platform: DevicePlatform,
    @Column(name = "push_token", nullable = false, unique = true) val pushToken: String,
    @Column(nullable = false) var active: Boolean,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Version var version: Long = 0,
) {
    fun activateFor(recipientType: RecipientType, recipientId: UUID, now: Instant) {
        this.recipientType = recipientType
        this.recipientId = recipientId
        active = true
        updatedAt = now
    }

    fun deactivate(now: Instant) {
        active = false
        updatedAt = now
    }
}

enum class DeliveryChannel { PUSH, SMS }
enum class DeliveryStatus { PENDING, PROCESSING, DELIVERED, DEAD }

@Entity
@Table(name = "notification_delivery")
class NotificationDelivery(
    @Id val id: UUID,
    @Column(name = "notification_id", nullable = false) val notificationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) val channel: DeliveryChannel,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) var status: DeliveryStatus,
    @Column(nullable = false) var attempts: Int,
    @Column(name = "next_attempt_at", nullable = false) var nextAttemptAt: Instant,
    @Column(name = "claimed_by") var claimedBy: UUID? = null,
    @Column(name = "claim_until") var claimUntil: Instant? = null,
    @Column(name = "last_error") var lastError: String? = null,
    @Column(name = "delivered_at") var deliveredAt: Instant? = null,
)
