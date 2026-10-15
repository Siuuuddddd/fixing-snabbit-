package com.snabbit.prototype.user.domain

import com.snabbit.prototype.user.domain.enums.AuthEmailChallengePurpose
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
@Table(name = "auth_email_challenge")
class AuthEmailChallenge(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) val purpose: AuthEmailChallengePurpose,
    @Column(name = "code_hmac", nullable = false, length = 64) var codeHmac: String,
    @Column(name = "attempt_count", nullable = false) var attemptCount: Int,
    @Column(name = "send_count", nullable = false) var sendCount: Int,
    @Column(name = "last_sent_at", nullable = false) var lastSentAt: Instant,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
    @Column(name = "consumed_at") var consumedAt: Instant?,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Version var version: Long = 0,
)
