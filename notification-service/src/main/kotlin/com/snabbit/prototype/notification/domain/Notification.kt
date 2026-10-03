package com.snabbit.prototype.notification.domain

import com.snabbit.prototype.notification.domain.enums.RecipientType

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
