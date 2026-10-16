package com.snabbit.prototype.user.service

import com.snabbit.prototype.platform.IndianPhoneNumber
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.user.auth.AuthProperties
import com.snabbit.prototype.user.auth.OtpChallengeStore
import com.snabbit.prototype.user.auth.PhoneAuthRateLimiter
import com.snabbit.prototype.user.dto.response.CustomerContactSnapshot
import com.snabbit.prototype.user.dto.response.PhoneVerificationAccepted
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.exception.InvalidOrExpiredCode
import com.snabbit.prototype.user.exception.PhoneInUse
import com.snabbit.prototype.user.exception.TooManyCodeAttempts
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerPhoneVerificationService(
    private val users: UserProfileRepository,
    private val provider: PhoneVerificationProvider,
    private val challenges: OtpChallengeStore,
    private val rateLimiter: PhoneAuthRateLimiter,
    private val authProperties: AuthProperties,
    private val clock: Clock,
) {
    fun send(userId: UUID, authenticatedUserId: UUID, rawPhone: String, ipAddress: String): PhoneVerificationAccepted {
        requireOwner(userId, authenticatedUserId)
        if (!users.existsById(userId)) throw UserNotFound(userId)
        val phone = IndianPhoneNumber.normalize(rawPhone)
        rateLimiter.acquireSend(phone, ipAddress)
        return try {
            challenges.clear(key(userId, phone))
            val result = provider.start(phone)
            PhoneVerificationAccepted(mask(phone), minOf(result.expiresInSeconds, 30))
        } catch (error: RuntimeException) {
            rateLimiter.releaseFailedSend(phone)
            throw error
        }
    }

    @Transactional(noRollbackFor = [InvalidOrExpiredCode::class, TooManyCodeAttempts::class])
    fun verify(
        userId: UUID,
        authenticatedUserId: UUID,
        rawPhone: String,
        code: String,
        ipAddress: String,
    ): CustomerContactSnapshot {
        requireOwner(userId, authenticatedUserId)
        val phone = IndianPhoneNumber.normalize(rawPhone)
        rateLimiter.checkVerification(ipAddress)
        val challengeKey = key(userId, phone)
        return when (provider.check(phone, code)) {
            VerificationStatus.APPROVED -> {
                val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
                val currentOwner = users.findByPhoneForUpdate(phone)
                if (currentOwner != null && currentOwner.id != userId) throw PhoneInUse()
                val now = clock.instant()
                user.verifyPhone(phone, now)
                challenges.clear(challengeKey)
                provider.clear(phone)
                CustomerContactSnapshot(user.id, user.phoneE164, user.displayName, true)
            }
            VerificationStatus.WRONG_CODE -> {
                val attempts = challenges.incrementAttempts(challengeKey, authProperties.otpTtl)
                if (attempts >= 5) {
                    challenges.clear(challengeKey)
                    provider.clear(phone)
                    throw TooManyCodeAttempts()
                }
                throw InvalidOrExpiredCode()
            }
            VerificationStatus.EXPIRED -> {
                challenges.clear(challengeKey)
                throw InvalidOrExpiredCode()
            }
            VerificationStatus.MAX_ATTEMPTS -> {
                challenges.clear(challengeKey)
                provider.clear(phone)
                throw TooManyCodeAttempts()
            }
        }
    }

    private fun requireOwner(userId: UUID, authenticatedUserId: UUID) {
        if (userId != authenticatedUserId) throw CustomerAccessDenied()
    }

    private fun key(userId: UUID, phone: String): String = "profile-phone:$userId:${OpaqueToken.hash(phone)}"
    private fun mask(phone: String): String = "+91 ••••••${phone.takeLast(4)}"
}
