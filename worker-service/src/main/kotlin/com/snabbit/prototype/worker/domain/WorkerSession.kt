package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_session")
class WorkerSession(
    @Id val id: UUID,
    @Column(name = "worker_id", nullable = false) val workerId: UUID,
    @Column(nullable = false) val role: String,
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64) var refreshTokenHash: String,
    @Column(name = "token_family_id", nullable = false) val tokenFamilyId: UUID,
    @Column(name = "device_id") val deviceId: String?,
    @Column(name = "device_name") val deviceName: String?,
    @Column(name = "shared_login", nullable = false) val sharedLogin: Boolean,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "last_used_at", nullable = false) var lastUsedAt: Instant,
    @Column(name = "idle_expires_at", nullable = false) var idleExpiresAt: Instant,
    @Column(name = "absolute_expires_at") val absoluteExpiresAt: Instant?,
    @Column(name = "revoked_at") var revokedAt: Instant? = null,
    @Column(name = "revoke_reason") var revokeReason: String? = null,
    @Version var version: Long = 0,
) {
    fun revoke(at: Instant, reason: String) {
        if (revokedAt == null) {
            revokedAt = at
            revokeReason = reason.take(80)
        }
    }
}
