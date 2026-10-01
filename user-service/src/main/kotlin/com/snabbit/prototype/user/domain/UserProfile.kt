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

enum class UserStatus { ACTIVE, SUSPENDED, DELETED }

@Entity
@Table(name = "user_profile")
class UserProfile(
    @Id
    val id: UUID,
    @Column(name = "phone_e164", nullable = false, unique = true)
    val phoneE164: String,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus,
    @Column(name = "plus_active", nullable = false)
    var plusActive: Boolean,
    @Column(name = "plus_expires_at")
    var plusExpiresAt: Instant?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun hasActivePlus(now: Instant): Boolean =
        plusActive && (plusExpiresAt == null || plusExpiresAt!!.isAfter(now))
}
