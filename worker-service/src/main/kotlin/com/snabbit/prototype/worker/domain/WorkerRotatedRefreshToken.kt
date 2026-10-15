package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_rotated_refresh_token")
class WorkerRotatedRefreshToken(
    @Id val id: UUID,
    @Column(name = "session_id", nullable = false) val sessionId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true) val tokenHash: String,
    @Column(name = "rotated_at", nullable = false) val rotatedAt: Instant,
)
