package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.AuthEmailChallengePurpose
import com.snabbit.prototype.user.domain.enums.UserAuthProvider
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.request.ChangePasswordRequest
import com.snabbit.prototype.user.dto.request.EmailLoginRequest
import com.snabbit.prototype.user.dto.request.EmailRegisterRequest
import com.snabbit.prototype.user.dto.request.ResetPasswordRequest
import com.snabbit.prototype.user.dto.request.VerifyEmailRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.exception.AuthRateLimited
import com.snabbit.prototype.user.exception.CustomerAccountDisabled
import com.snabbit.prototype.user.exception.EmailNotVerified
import com.snabbit.prototype.user.exception.InvalidCredentials
import com.snabbit.prototype.user.exception.InvalidOrExpiredCode
import com.snabbit.prototype.user.exception.PasswordNotSet
import com.snabbit.prototype.user.exception.TermsNotAccepted
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerEmailAuthService(
    private val users: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    private val policy: PasswordPolicy,
    private val challenges: AuthEmailChallengeService,
    private val emailSender: AuthEmailSender,
    private val sessions: CustomerSessionService,
    private val rateLimiter: EmailAuthRateLimiter,
    private val properties: EmailAuthProperties,
    private val clock: Clock,
) {
    private val dummyHash = passwordEncoder.encode(OpaqueToken.generate())

    @Transactional
    fun register(request: EmailRegisterRequest, ipAddress: String) {
        val email = EmailIdentity.normalize(request.email)
        rateLimiter.check("register", email, ipAddress)
        if (!request.termsAccepted) throw TermsNotAccepted()
        val name = request.name.trim()
        policy.validate(request.password, email, name)
        val existing = users.findByEmailForUpdate(email)
        if (existing?.emailVerified == true) {
            emailSender.sendExistingAccountNotice(email)
            return
        }
        val now = clock.instant()
        val user = existing ?: users.saveAndFlush(
            UserProfile(
                id = UUID.randomUUID(), phoneE164 = null, displayName = name, status = UserStatus.ACTIVE,
                plusActive = false, plusExpiresAt = null, createdAt = now, updatedAt = now,
                email = email, authProvider = UserAuthProvider.PASSWORD,
                authProviders = mutableSetOf(UserAuthProvider.PASSWORD),
            ),
        )
        if (user.passwordHash == null && user.googleSub == null) {
            user.passwordHash = passwordEncoder.encode(request.password)
            user.passwordUpdatedAt = now
            user.displayName = name
            user.termsAcceptedAt = now
            user.termsVersion = properties.termsVersion
            user.authProvider = UserAuthProvider.PASSWORD
            user.authProviders.remove(UserAuthProvider.PHONE)
            user.authProviders.add(UserAuthProvider.PASSWORD)
            user.updatedAt = now
        }
        sendVerification(user)
    }

    @Transactional
    fun verify(request: VerifyEmailRequest, ipAddress: String): CustomerSessionResponse {
        val email = EmailIdentity.normalize(request.email)
        rateLimiter.check("verify", email, ipAddress)
        val user = users.findByEmailForUpdate(email) ?: throw InvalidOrExpiredCode()
        challenges.verify(user.id, AuthEmailChallengePurpose.VERIFY_EMAIL, request.code)
        val now = clock.instant()
        user.emailVerified = true
        user.emailVerifiedAt = now
        user.authProviders.add(UserAuthProvider.PASSWORD)
        user.updatedAt = now
        return sessions.create(user, request.deviceId, request.deviceName, true)
    }

    @Transactional
    fun resend(rawEmail: String, ipAddress: String) {
        val email = EmailIdentity.normalize(rawEmail)
        rateLimiter.check("resend", email, ipAddress)
        val user = users.findByEmailForUpdate(email)
        if (user != null && !user.emailVerified && user.passwordHash != null) sendVerification(user)
    }

    @Transactional
    fun login(request: EmailLoginRequest, ipAddress: String): CustomerSessionResponse {
        val email = EmailIdentity.normalize(request.email)
        rateLimiter.check("login", email, ipAddress)
        val user = users.findByEmailForUpdate(email)
        val matches = passwordEncoder.matches(request.password, user?.passwordHash ?: dummyHash)
        if (user == null || user.passwordHash == null || !matches) throw InvalidCredentials()
        if (user.status != UserStatus.ACTIVE) throw CustomerAccountDisabled()
        if (!user.emailVerified) {
            runCatching {
                val code = challenges.issueIndependent(user.id, AuthEmailChallengePurpose.VERIFY_EMAIL)
                emailSender.sendVerificationCode(email, code)
            }.onFailure { if (it !is AuthRateLimited) throw it }
            throw EmailNotVerified()
        }
        if (passwordEncoder.upgradeEncoding(user.passwordHash!!)) {
            user.passwordHash = passwordEncoder.encode(request.password)
            user.passwordUpdatedAt = clock.instant()
        }
        user.lastLoginAt = clock.instant()
        user.updatedAt = clock.instant()
        return sessions.create(user, request.deviceId, request.deviceName, false)
    }

    @Transactional
    fun forgotPassword(rawEmail: String, ipAddress: String) {
        val email = EmailIdentity.normalize(rawEmail)
        rateLimiter.check("forgot", email, ipAddress)
        val user = users.findByEmailForUpdate(email)
        if (user == null) {
            passwordEncoder.matches(email, dummyHash)
            return
        }
        if (user.emailVerified && user.status == UserStatus.ACTIVE) {
            val code = challenges.issue(user.id, AuthEmailChallengePurpose.RESET_PASSWORD)
            emailSender.sendResetCode(email, code)
        }
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest, ipAddress: String) {
        val email = EmailIdentity.normalize(request.email)
        rateLimiter.check("reset", email, ipAddress)
        val user = users.findByEmailForUpdate(email) ?: throw InvalidOrExpiredCode()
        policy.validate(request.newPassword, email, user.displayName)
        challenges.verify(user.id, AuthEmailChallengePurpose.RESET_PASSWORD, request.code)
        val now = clock.instant()
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        user.passwordUpdatedAt = now
        user.authProviders.add(UserAuthProvider.PASSWORD)
        if (user.googleSub == null) user.authProvider = UserAuthProvider.PASSWORD
        user.updatedAt = now
        sessions.invalidateAllSessions(user.id, "PASSWORD_RESET")
    }

    @Transactional
    fun changePassword(userId: UUID, sessionId: UUID, request: ChangePasswordRequest): CustomerSessionResponse {
        val user = users.findForUpdate(userId) ?: throw InvalidCredentials()
        val currentHash = user.passwordHash ?: throw PasswordNotSet()
        if (!passwordEncoder.matches(request.currentPassword, currentHash)) throw InvalidCredentials()
        val email = requireNotNull(user.email)
        policy.validate(request.newPassword, email, user.displayName)
        val now = clock.instant()
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        user.passwordUpdatedAt = now
        user.authProviders.add(UserAuthProvider.PASSWORD)
        user.updatedAt = now
        return sessions.rotateAfterCredentialChange(userId, sessionId)
    }

    private fun sendVerification(user: UserProfile) {
        val code = challenges.issue(user.id, AuthEmailChallengePurpose.VERIFY_EMAIL)
        emailSender.sendVerificationCode(requireNotNull(user.email), code)
    }
}
