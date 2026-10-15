package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.domain.CustomerRotatedRefreshToken
import com.snabbit.prototype.user.domain.CustomerSession
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.exception.RefreshInProgress
import com.snabbit.prototype.user.exception.SessionRevoked
import com.snabbit.prototype.user.repository.CustomerRotatedRefreshTokenRepository
import com.snabbit.prototype.user.repository.CustomerSessionRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator

class CustomerSessionServiceTest {
    private val sessions = mockk<CustomerSessionRepository>()
    private val rotated = mockk<CustomerRotatedRefreshTokenRepository>()
    private val users = mockk<UserProfileRepository>()
    private val revocations = mockk<CustomerSessionRevocationStore>(relaxed = true)
    private val rateLimiter = mockk<CustomerAuthRateLimiter>(relaxed = true)
    private val start = Instant.parse("2026-10-15T04:00:00Z")
    private val clock = MutableClock(start)
    private val secret = "session-unit-test-secret-that-is-at-least-32-bytes"
    private val properties = AuthProperties(
        issuer = "https://auth.snabbit.test", jwtSecret = secret, otpPepper = "pepper",
        otpTtl = Duration.ofMinutes(5), requestCooldown = Duration.ofMinutes(1),
        accessTokenTtl = Duration.ofMinutes(15), maxAttempts = 5, exposeCode = false,
        refreshIdleTtl = Duration.ofDays(180), refreshGraceWindow = Duration.ofSeconds(10), maxActiveSessions = 10,
    )
    private val service = CustomerSessionService(
        sessions, rotated, users, revocations, rateLimiter, AuthConfiguration().jwtEncoder(properties), properties, clock,
    )
    private val user = profile()

    @Test
    fun `create issues hashed refresh state and access token with session id`() {
        val saved = slot<CustomerSession>()
        every { sessions.findActiveByUserId(user.id) } returns emptyList()
        every { sessions.save(capture(saved)) } answers { firstArg() }

        val response = service.create(user, "device", "Test device", true)
        val jwt = decoder().decode(response.accessToken)

        assertEquals(saved.captured.id.toString(), jwt.getClaimAsString("sid"))
        assertEquals(user.id.toString(), jwt.subject)
        assertEquals(listOf("CUSTOMER"), jwt.getClaimAsStringList("roles"))
        assertNotEquals(response.refreshToken, saved.captured.refreshTokenHash)
        assertEquals(OpaqueToken.hash(response.refreshToken), saved.captured.refreshTokenHash)
    }

    @Test
    fun `refresh rotates once and concurrent loser receives retryable grace response`() {
        val session = session(refreshHash = OpaqueToken.hash("original-refresh"))
        every { sessions.findByRefreshHashForUpdate(session.refreshTokenHash) } returns session andThen null
        every { users.findForUpdate(user.id) } returns user
        every { rotated.save(any()) } answers { firstArg() }

        val response = service.refresh("original-refresh")
        val used = CustomerRotatedRefreshToken(UUID.randomUUID(), session.id, OpaqueToken.hash("original-refresh"), start)
        every { rotated.findByTokenHash(used.tokenHash) } returns used
        every { sessions.findForUpdate(session.id) } returns session

        assertNotEquals("original-refresh", response.refreshToken)
        assertEquals(OpaqueToken.hash(response.refreshToken), session.refreshTokenHash)
        assertThrows(RefreshInProgress::class.java) { service.refresh("original-refresh") }
    }

    @Test
    fun `reuse after grace revokes the token family`() {
        val usedHash = OpaqueToken.hash("rotated-refresh")
        val session = session()
        val used = CustomerRotatedRefreshToken(UUID.randomUUID(), session.id, usedHash, start)
        clock.advance(Duration.ofSeconds(11))
        every { sessions.findByRefreshHashForUpdate(usedHash) } returns null
        every { rotated.findByTokenHash(usedHash) } returns used
        every { sessions.findForUpdate(session.id) } returns session
        every { sessions.findByTokenFamilyId(session.tokenFamilyId) } returns listOf(session)

        assertThrows(SessionRevoked::class.java) { service.refresh("rotated-refresh") }
        assertEquals("REFRESH_REUSE", session.revokeReason)
        verify { revocations.revoke(session.id, properties.accessTokenTtl) }
    }

    @Test
    fun `idle expiry logout logout-all and session cap revoke sessions`() {
        val expired = session().apply { idleExpiresAt = start.minusSeconds(1) }
        every { sessions.findByRefreshHashForUpdate(any()) } returns expired
        every { users.findForUpdate(user.id) } returns user
        assertThrows(SessionRevoked::class.java) { service.refresh("expired") }

        val logout = session()
        every { sessions.findForUpdate(logout.id) } returns logout
        service.logout(user.id, logout.id)
        assertEquals("LOGOUT", logout.revokeReason)

        val all = listOf(session(), session())
        every { users.findForUpdate(user.id) } returns user
        every { sessions.findActiveByUserId(user.id) } returns all
        service.logoutAll(user.id)
        assertEquals(start, user.sessionsValidAfter)
        assertEquals(listOf("LOGOUT_ALL", "LOGOUT_ALL"), all.map(CustomerSession::revokeReason))

        val atLimit = List(10) { session(createdAt = start.plusSeconds(it.toLong())) }
        every { sessions.findActiveByUserId(user.id) } returns atLimit
        every { sessions.save(any()) } answers { firstArg() }
        service.create(user, null, null, false)
        assertEquals("SESSION_LIMIT", atLimit.first().revokeReason)
    }

    @Test
    fun `absolute expiry and account cutoff invalidate refresh sessions`() {
        val absoluteExpired = session(absoluteExpiresAt = start.minusSeconds(1))
        every { sessions.findByRefreshHashForUpdate(any()) } returns absoluteExpired
        every { users.findForUpdate(user.id) } returns user
        assertThrows(SessionRevoked::class.java) { service.refresh("absolute-expired") }
        assertEquals("SESSION_EXPIRED", absoluteExpired.revokeReason)

        val beforeCutoff = session(createdAt = start.minusSeconds(1))
        user.sessionsValidAfter = start
        every { sessions.findByRefreshHashForUpdate(any()) } returns beforeCutoff
        assertThrows(SessionRevoked::class.java) { service.refresh("before-cutoff") }
        assertEquals("SESSION_EXPIRED", beforeCutoff.revokeReason)
    }

    private fun session(
        refreshHash: String = OpaqueToken.hash(UUID.randomUUID().toString()),
        createdAt: Instant = start,
        absoluteExpiresAt: Instant? = null,
    ) = CustomerSession(
        id = UUID.randomUUID(), userId = user.id, role = "CUSTOMER", refreshTokenHash = refreshHash,
        tokenFamilyId = UUID.randomUUID(), deviceId = null, deviceName = null, sharedLogin = false,
        createdAt = createdAt, lastUsedAt = createdAt, idleExpiresAt = createdAt.plus(Duration.ofDays(180)),
        absoluteExpiresAt = absoluteExpiresAt,
    )

    private fun profile() = UserProfile(
        id = UUID.randomUUID(), phoneE164 = null, displayName = "Customer", status = UserStatus.ACTIVE,
        plusActive = false, plusExpiresAt = null, createdAt = start, updatedAt = start,
        googleSub = "subject", email = "customer" + "@" + "example.test", emailVerified = true,
    )

    private fun decoder(): NimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(
        SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"),
    ).macAlgorithm(MacAlgorithm.HS256).build().also {
        it.setJwtValidator(JwtTimestampValidator().apply { setClock(Clock.fixed(start, ZoneOffset.UTC)) })
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
