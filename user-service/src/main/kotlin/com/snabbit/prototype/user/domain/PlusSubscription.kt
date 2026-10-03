package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "plus_subscription")
class PlusSubscription(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "source_reference", nullable = false, unique = true) val sourceReference: String,
    @Column(name = "starts_at", nullable = false) val startsAt: Instant,
    @Column(name = "expires_at", nullable = false) val expiresAt: Instant,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
)
