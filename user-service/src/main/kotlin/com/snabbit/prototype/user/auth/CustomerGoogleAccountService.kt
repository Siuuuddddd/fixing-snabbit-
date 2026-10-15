package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.exception.CustomerAccountConflict
import com.snabbit.prototype.user.exception.CustomerAccountDisabled
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerGoogleAccountService(
    private val users: UserProfileRepository,
    private val clock: Clock,
) {
    @Transactional
    fun resolve(identity: GoogleIdentity): CustomerGoogleAccount {
        users.findByGoogleSub(identity.subject)?.let { existing ->
            ensureActive(existing)
            existing.applyGoogleIdentity(
                identity.subject,
                identity.email,
                displayName(identity, existing.displayName),
                identity.pictureUrl,
                clock.instant(),
            )
            return CustomerGoogleAccount(existing, false)
        }

        val byEmail = users.findByEmailForUpdate(identity.email)
        if (byEmail != null) {
            ensureActive(byEmail)
            if (byEmail.googleSub != null && byEmail.googleSub != identity.subject) {
                throw CustomerAccountConflict()
            }
            val unverifiedPassword = !byEmail.emailVerified && byEmail.passwordHash != null
            byEmail.applyGoogleIdentity(
                identity.subject,
                identity.email,
                displayName(identity, byEmail.displayName),
                identity.pictureUrl,
                clock.instant(),
            )
            if (unverifiedPassword) {
                byEmail.passwordHash = null
                byEmail.passwordUpdatedAt = null
                byEmail.authProviders.remove(com.snabbit.prototype.user.domain.enums.UserAuthProvider.PASSWORD)
            }
            return CustomerGoogleAccount(byEmail, false)
        }

        val now = clock.instant()
        val created = UserProfile(
            id = UUID.randomUUID(),
            phoneE164 = null,
            displayName = displayName(identity, null),
            status = UserStatus.ACTIVE,
            plusActive = false,
            plusExpiresAt = null,
            createdAt = now,
            updatedAt = now,
            authProvider = com.snabbit.prototype.user.domain.enums.UserAuthProvider.GOOGLE,
            authProviders = mutableSetOf(com.snabbit.prototype.user.domain.enums.UserAuthProvider.GOOGLE),
        )
        created.applyGoogleIdentity(
            identity.subject,
            identity.email,
            created.displayName,
            identity.pictureUrl,
            now,
        )
        return CustomerGoogleAccount(users.save(created), true)
    }

    private fun ensureActive(user: UserProfile) {
        if (user.status != UserStatus.ACTIVE) throw CustomerAccountDisabled()
    }

    private fun displayName(identity: GoogleIdentity, current: String?): String =
        identity.displayName?.trim()?.takeIf(String::isNotEmpty)
            ?: current?.trim()?.takeIf(String::isNotEmpty)
            ?: identity.email.substringBefore('@').take(120)
}

data class CustomerGoogleAccount(
    val profile: UserProfile,
    val newUser: Boolean,
)
