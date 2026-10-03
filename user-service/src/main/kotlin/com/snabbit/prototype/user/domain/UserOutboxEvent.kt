package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_event")
class UserOutboxEvent(
    @Id val id: UUID,
    @Column(name = "aggregate_type", nullable = false) val aggregateType: String,
    @Column(name = "aggregate_id", nullable = false) val aggregateId: String,
    @Column(name = "event_type", nullable = false) val eventType: String,
    @Column(name = "schema_version", nullable = false) val schemaVersion: Int = 1,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") val payload: String,
    @Column(name = "occurred_at", nullable = false) val occurredAt: Instant,
    @Column(name = "published_at") var publishedAt: Instant? = null,
)
