package com.snabbit.prototype.notification.domain

import com.snabbit.prototype.notification.domain.enums.DeliveryChannel
import com.snabbit.prototype.notification.domain.enums.DeliveryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

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
