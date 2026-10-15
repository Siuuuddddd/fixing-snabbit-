package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.domain.AuthEmailChallenge
import com.snabbit.prototype.user.domain.enums.AuthEmailChallengePurpose
import com.snabbit.prototype.user.exception.AuthRateLimited
import com.snabbit.prototype.user.exception.InvalidOrExpiredCode
import com.snabbit.prototype.user.exception.TooManyCodeAttempts
import com.snabbit.prototype.user.repository.AuthEmailChallengeRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation

@Service
class AuthEmailChallengeService(
    private val challenges: AuthEmailChallengeRepository,
    private val properties: EmailAuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(userId: UUID, purpose: AuthEmailChallengePurpose): String = issueInternal(userId, purpose)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun issueIndependent(userId: UUID, purpose: AuthEmailChallengePurpose): String = issueInternal(userId, purpose)

    private fun issueInternal(userId: UUID, purpose: AuthEmailChallengePurpose): String {
        val now = clock.instant()
        val active = challenges.findActiveForUpdate(userId, purpose)
        if (active != null) {
            val cooldownEnds = active.lastSentAt.plus(properties.resendCooldown)
            if (now.isBefore(cooldownEnds)) throw AuthRateLimited(Duration.between(now, cooldownEnds).seconds.coerceAtLeast(1))
            val hourEnds = active.createdAt.plus(Duration.ofHours(1))
            if (now.isBefore(hourEnds) && active.sendCount >= properties.maxSendsPerHour) {
                throw AuthRateLimited(Duration.between(now, hourEnds).seconds.coerceAtLeast(1))
            }
            if (!now.isBefore(hourEnds)) {
                active.consumedAt = now
                challenges.save(active)
                return create(userId, purpose, now)
            }
            val code = code()
            active.codeHmac = hmac(code)
            active.attemptCount = 0
            active.sendCount += 1
            active.lastSentAt = now
            active.expiresAt = now.plus(properties.codeTtl)
            return code
        }
        return create(userId, purpose, now)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = [InvalidOrExpiredCode::class, TooManyCodeAttempts::class])
    fun verify(userId: UUID, purpose: AuthEmailChallengePurpose, rawCode: String) {
        val now = clock.instant()
        val challenge = challenges.findActiveForUpdate(userId, purpose) ?: throw InvalidOrExpiredCode()
        if (!now.isBefore(challenge.expiresAt)) {
            challenge.consumedAt = now
            throw InvalidOrExpiredCode()
        }
        val expected = challenge.codeHmac.toByteArray(StandardCharsets.US_ASCII)
        val actual = hmac(rawCode).toByteArray(StandardCharsets.US_ASCII)
        if (!MessageDigest.isEqual(expected, actual)) {
            challenge.attemptCount += 1
            if (challenge.attemptCount >= properties.codeMaxAttempts) {
                challenge.consumedAt = now
                throw TooManyCodeAttempts()
            }
            throw InvalidOrExpiredCode()
        }
        challenge.consumedAt = now
    }

    private fun create(userId: UUID, purpose: AuthEmailChallengePurpose, now: java.time.Instant): String {
        val code = code()
        challenges.save(
            AuthEmailChallenge(
                id = UUID.randomUUID(), userId = userId, purpose = purpose, codeHmac = hmac(code),
                attemptCount = 0, sendCount = 1, lastSentAt = now, expiresAt = now.plus(properties.codeTtl),
                consumedAt = null, createdAt = now,
            ),
        )
        return code
    }

    private fun code(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    private fun hmac(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.codeHmacSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }
}
