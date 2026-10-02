package com.snabbit.prototype.user.staff

import com.snabbit.prototype.user.auth.AuthConfiguration
import com.snabbit.prototype.user.auth.AuthProperties
import com.snabbit.prototype.user.auth.OtpChallengeStore
import com.snabbit.prototype.user.auth.OtpDelivery
import com.snabbit.prototype.user.domain.StaffAccount
import com.snabbit.prototype.user.domain.StaffRole
import com.snabbit.prototype.user.domain.StaffStatus
import com.snabbit.prototype.user.repository.StaffAccountRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class StaffAuthenticationServiceTest {
    private val staff = mockk<StaffAccountRepository>()
    private val challenges = ChallengeStore()
    private val delivery = mockk<OtpDelivery>()
    private val code = slot<String>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val secret = "staff-test-jwt-secret-that-is-at-least-32-bytes"
    private val properties = AuthProperties(
        "https://auth.snabbit.test", secret, "staff-test-pepper",
        Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(15), 5, true,
    )
    private val service = StaffAuthenticationService(
        staff, challenges, delivery, AuthConfiguration().jwtEncoder(properties), properties,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `registered support agent receives role-separated signed token`() {
        val account = StaffAccount(
            UUID.randomUUID(), "+919800000001", "Support", StaffRole.SUPPORT_AGENT,
            StaffStatus.ACTIVE, now,
        )
        every { staff.findByPhoneE164(account.phoneE164) } returns account
        every { delivery.deliver(account.phoneE164, capture(code)) } returns Unit

        val request = service.requestOtp(account.phoneE164)
        val token = service.verifyOtp(account.phoneE164, request.debugOtp!!)
        val jwt = decoder().decode(token.accessToken)

        assertEquals(code.captured, request.debugOtp)
        assertEquals(account.id.toString(), jwt.subject)
        assertEquals(listOf("SUPPORT_AGENT"), jwt.getClaimAsStringList("roles"))
    }

    private fun decoder(): NimbusJwtDecoder {
        val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().also { decoder ->
            decoder.setJwtValidator(JwtTimestampValidator().apply { setClock(Clock.fixed(now, ZoneOffset.UTC)) })
        }
    }

    private class ChallengeStore : OtpChallengeStore {
        private var codeHash: String? = null
        private var attempts = 0L

        override fun acquireRequestCooldown(key: String, ttl: Duration) = true
        override fun save(key: String, codeHash: String, ttl: Duration) {
            this.codeHash = codeHash
        }
        override fun codeHash(key: String) = codeHash
        override fun incrementAttempts(key: String, ttl: Duration) = ++attempts
        override fun clear(key: String) {
            codeHash = null
        }
    }
}
