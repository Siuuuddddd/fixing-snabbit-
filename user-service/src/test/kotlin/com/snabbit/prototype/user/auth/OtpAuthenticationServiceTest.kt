package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.UserStatus
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class OtpAuthenticationServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val store = InMemoryChallengeStore()
    private val delivery = mockk<OtpDelivery>()
    private val deliveredCode = slot<String>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val secret = "unit-test-jwt-secret-that-is-at-least-32-bytes"
    private val properties = AuthProperties(
        issuer = "https://auth.snabbit.test",
        jwtSecret = secret,
        otpPepper = "test-pepper",
        otpTtl = Duration.ofMinutes(5),
        requestCooldown = Duration.ofMinutes(1),
        accessTokenTtl = Duration.ofMinutes(15),
        maxAttempts = 5,
        exposeCode = true,
    )
    private val service = OtpAuthenticationService(
        users,
        store,
        delivery,
        AuthConfiguration().jwtEncoder(properties),
        properties,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val phone = "+919876543210"

    @Test
    fun `registered Indian phone receives one-time OTP and signed customer token`() {
        val user = user()
        every { users.findByPhoneE164(phone) } returns user
        every { delivery.deliver(phone, capture(deliveredCode)) } returns Unit

        val requested = service.requestOtp(phone)
        val verified = service.verifyOtp(phone, requested.debugOtp!!)

        assertEquals(deliveredCode.captured, requested.debugOtp)
        assertEquals("Bearer", verified.tokenType)
        assertEquals(900, verified.expiresInSeconds)
        val jwt = decoder().decode(verified.accessToken)
        assertEquals(user.id.toString(), jwt.subject)
        assertEquals("https://auth.snabbit.test", jwt.issuer.toString())
        assertEquals(listOf("CUSTOMER"), jwt.getClaimAsStringList("roles"))
        assertNull(store.hash)
        assertThrows(InvalidOtp::class.java) { service.verifyOtp(phone, requested.debugOtp!!) }
    }

    @Test
    fun `OTP requests are throttled per phone`() {
        every { users.findByPhoneE164(phone) } returns user()
        every { delivery.deliver(phone, any()) } returns Unit

        service.requestOtp(phone)

        assertThrows(OtpRateLimited::class.java) { service.requestOtp(phone) }
    }

    @Test
    fun `unregistered phone receives generic response without a challenge`() {
        every { users.findByPhoneE164(phone) } returns null

        val response = service.requestOtp(phone)

        assertEquals("OTP_SENT", response.status)
        assertNull(response.debugOtp)
        assertNull(store.hash)
    }

    @Test
    fun `challenge is destroyed after maximum failed attempts`() {
        every { users.findByPhoneE164(phone) } returns user()
        every { delivery.deliver(phone, capture(deliveredCode)) } returns Unit
        val response = service.requestOtp(phone)
        val wrongCode = if (response.debugOtp == "000000") "111111" else "000000"

        repeat(properties.maxAttempts + 1) {
            assertThrows(InvalidOtp::class.java) { service.verifyOtp(phone, wrongCode) }
        }

        assertNull(store.hash)
        assertThrows(InvalidOtp::class.java) { service.verifyOtp(phone, response.debugOtp!!) }
    }

    private fun decoder(): NimbusJwtDecoder {
        val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().also { decoder ->
            decoder.setJwtValidator(JwtTimestampValidator().apply { setClock(Clock.fixed(now, ZoneOffset.UTC)) })
        }
    }

    private fun user() = UserProfile(
        UUID.randomUUID(),
        phone,
        "Prototype Customer",
        UserStatus.ACTIVE,
        true,
        now.plusSeconds(3600),
        now,
        now,
    )

    private class InMemoryChallengeStore : OtpChallengeStore {
        var cooldown = false
        var hash: String? = null
        var attempts = 0L

        override fun acquireRequestCooldown(key: String, ttl: Duration): Boolean = if (cooldown) {
            false
        } else {
            cooldown = true
            true
        }

        override fun save(key: String, codeHash: String, ttl: Duration) {
            hash = codeHash
            attempts = 0
        }

        override fun codeHash(key: String): String? = hash

        override fun incrementAttempts(key: String, ttl: Duration): Long = ++attempts

        override fun clear(key: String) {
            hash = null
            attempts = 0
        }
    }
}
