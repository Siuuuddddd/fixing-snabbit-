package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.domain.AuthEmailChallenge
import com.snabbit.prototype.user.domain.enums.AuthEmailChallengePurpose
import com.snabbit.prototype.user.exception.AuthRateLimited
import com.snabbit.prototype.user.repository.AuthEmailChallengeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthEmailChallengeServiceTest {
    @Test
    fun `resend cooldown and hourly cap are enforced while a new code replaces the old one`() {
        val repository = mockk<AuthEmailChallengeRepository>()
        val saved = slot<AuthEmailChallenge>()
        var active: AuthEmailChallenge? = null
        every { repository.findActiveForUpdate(any(), any()) } answers { active }
        every { repository.save(capture(saved)) } answers { saved.captured.also { active = it } }
        val clock = MutableClock(Instant.parse("2030-01-01T00:00:00Z"))
        val service = AuthEmailChallengeService(
            repository,
            EmailAuthProperties(
                codeHmacSecret = OpaqueToken.generate(), resendCooldown = Duration.ofSeconds(60),
                maxSendsPerHour = 2,
            ),
            clock,
        )
        val userId = UUID.randomUUID()
        val first = service.issue(userId, AuthEmailChallengePurpose.VERIFY_EMAIL)
        assertThrows(AuthRateLimited::class.java) { service.issue(userId, AuthEmailChallengePurpose.VERIFY_EMAIL) }

        clock.advance(Duration.ofSeconds(61))
        val second = service.issue(userId, AuthEmailChallengePurpose.VERIFY_EMAIL)
        assertNotEquals(first, second)
        clock.advance(Duration.ofSeconds(61))
        val error = assertThrows(AuthRateLimited::class.java) {
            service.issue(userId, AuthEmailChallengePurpose.VERIFY_EMAIL)
        }
        assertTrue(error.retryAfterSeconds > 0)
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
