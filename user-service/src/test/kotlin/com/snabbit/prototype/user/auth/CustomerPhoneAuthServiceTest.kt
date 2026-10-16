package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationStartResult
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.user.config.DemoCustomerDevSeed
import com.snabbit.prototype.user.config.UserDevSeedProperties
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.dto.request.PhoneOtpVerifyRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.dto.response.CustomerSessionUser
import com.snabbit.prototype.user.exception.TooManyCodeAttempts
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class CustomerPhoneAuthServiceTest {
    private val provider = mockk<PhoneVerificationProvider>(relaxed = true)
    private val challenges = mockk<OtpChallengeStore>(relaxed = true)
    private val limiter = mockk<PhoneAuthRateLimiter>(relaxed = true)
    private val users = mockk<UserProfileRepository>()
    private val sessions = mockk<CustomerSessionService>()
    private val demo = mockk<ObjectProvider<DemoCustomerDevSeed>>()
    private val phoneProperties = PhoneAuthProperties()
    private val authProperties = AuthProperties(
        issuer = "https://auth.snabbit.test",
        jwtSecret = "unit-test-secret-that-is-at-least-32-bytes",
        otpPepper = "pepper",
        otpTtl = Duration.ofMinutes(5),
        requestCooldown = Duration.ofMinutes(1),
        accessTokenTtl = Duration.ofMinutes(15),
        maxAttempts = 5,
        exposeCode = false,
    )
    private val clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC)
    private val service = CustomerPhoneAuthService(
        provider, challenges, limiter, users, sessions, phoneProperties, authProperties,
        UserDevSeedProperties(), demo, clock,
    )

    init { every { demo.ifAvailable } returns null }

    @Test
    fun `request normalizes Indian phone and never returns provider debug code`() {
        every { provider.start("+919876543210") } returns VerificationStartResult(300, debugCode = "123456")

        val retryAfter = service.request("9876543210", "192.0.2.10")

        assertEquals(30, retryAfter)
        verify { limiter.acquireSend("+919876543210", "192.0.2.10") }
        verify { provider.start("+919876543210") }
    }

    @Test
    fun `approved code creates phone-only customer and forwards remember choice`() {
        every { provider.check("+919876543210", "123456") } returns VerificationStatus.APPROVED
        every { users.findByPhoneForUpdate("+919876543210") } returns null
        val saved = slot<UserProfile>()
        every { users.save(capture(saved)) } answers { firstArg() }
        val response = response()
        every { sessions.create(any(), any(), any(), true, false) } returns response

        val actual = service.verify(
            PhoneOtpVerifyRequest("9876543210", "123456", "Asha", rememberMe = false),
            "192.0.2.10",
        )

        assertEquals(response, actual)
        assertEquals("+919876543210", saved.captured.phoneE164)
        assertEquals(null, saved.captured.email)
        assertEquals("Asha", saved.captured.displayName)
        verify { sessions.create(saved.captured, null, null, true, false) }
    }

    @Test
    fun `fifth wrong code burns challenge`() {
        every { provider.check("+919876543210", "000000") } returns VerificationStatus.WRONG_CODE
        every { challenges.incrementAttempts(any(), authProperties.otpTtl) } returns 5

        assertThrows(TooManyCodeAttempts::class.java) {
            service.verify(PhoneOtpVerifyRequest("+919876543210", "000000"), "192.0.2.10")
        }

        verify { provider.clear("+919876543210") }
        verify { challenges.clear(any()) }
    }

    private fun response() = CustomerSessionResponse(
        accessToken = "access",
        expiresIn = 900,
        refreshToken = "refresh",
        user = CustomerSessionUser(
            UUID.randomUUID(), "Asha", null, "+919876543210", null,
            true, false, false, setOf("PHONE"),
        ),
    )
}
