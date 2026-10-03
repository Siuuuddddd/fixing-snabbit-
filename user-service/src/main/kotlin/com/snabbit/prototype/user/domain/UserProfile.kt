package com.snabbit.prototype.user.domain

import com.snabbit.prototype.user.domain.enums.UserStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

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
    @Column(name = "rating_sum", nullable = false)
    var ratingSum: Int = 0,
    @Column(name = "rating_count", nullable = false)
    var ratingCount: Int = 0,
    @Version
    var version: Long = 0,
) {
    fun hasActivePlus(now: Instant): Boolean =
        plusActive && (plusExpiresAt == null || plusExpiresAt!!.isAfter(now))

    fun averageRating(): BigDecimal = if (ratingCount == 0) {
        BigDecimal.ZERO
    } else {
        BigDecimal(ratingSum).divide(BigDecimal(ratingCount), 2, RoundingMode.HALF_UP)
    }
}
