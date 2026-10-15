package com.snabbit.prototype.user.domain

import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.domain.enums.UserAuthProvider
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
    @Column(name = "phone_e164", unique = true)
    var phoneE164: String?,
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
    @Column(name = "google_sub", unique = true)
    var googleSub: String? = null,
    @Column(length = 320)
    var email: String? = null,
    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,
    @Column(name = "picture_url", length = 2048)
    var pictureUrl: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    var authProvider: UserAuthProvider = UserAuthProvider.PHONE,
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
    @Column(name = "rating_sum", nullable = false)
    var ratingSum: Int = 0,
    @Column(name = "rating_count", nullable = false)
    var ratingCount: Int = 0,
    @Version
    var version: Long = 0,
) {
    fun applyGoogleIdentity(
        subject: String,
        verifiedEmail: String,
        name: String,
        picture: String?,
        now: Instant,
    ) {
        googleSub = subject
        email = verifiedEmail
        emailVerified = true
        displayName = name
        pictureUrl = picture
        authProvider = UserAuthProvider.GOOGLE
        lastLoginAt = now
        updatedAt = now
    }

    fun hasActivePlus(now: Instant): Boolean =
        plusActive && (plusExpiresAt == null || plusExpiresAt!!.isAfter(now))

    fun averageRating(): BigDecimal = if (ratingCount == 0) {
        BigDecimal.ZERO
    } else {
        BigDecimal(ratingSum).divide(BigDecimal(ratingCount), 2, RoundingMode.HALF_UP)
    }
}
