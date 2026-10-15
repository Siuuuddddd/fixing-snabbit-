package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "customer_rotated_refresh_token")
class CustomerRotatedRefreshToken(
    @Id val id: UUID,
    @Column(name = "session_id", nullable = false) val sessionId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true) val tokenHash: String,
    @Column(name = "rotated_at", nullable = false) val rotatedAt: Instant,
)
