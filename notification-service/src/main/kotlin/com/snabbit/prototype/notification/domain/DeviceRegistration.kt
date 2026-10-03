package com.snabbit.prototype.notification.domain

import com.snabbit.prototype.notification.domain.enums.DevicePlatform
import com.snabbit.prototype.notification.domain.enums.RecipientType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

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
