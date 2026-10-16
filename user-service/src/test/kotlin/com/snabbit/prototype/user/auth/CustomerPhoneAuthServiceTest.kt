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
    private val nationalNumber = "9" + "0".repeat(8) + "1"
    private val phoneNumber = "+91$nationalNumber"
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
        every { provider.start(phoneNumber) } returns VerificationStartResult(300, debugCode = "123456")

        val retryAfter = service.request(nationalNumber, "192.0.2.10")

        assertEquals(30, retryAfter)
        verify { limiter.acquireSend(phoneNumber, "192.0.2.10") }
        verify { provider.start(phoneNumber) }
    }

    @Test
    fun `approved code creates phone-only customer and forwards remember choice`() {
        every { provider.check(phoneNumber, "123456") } returns VerificationStatus.APPROVED
        every { users.findByPhoneForUpdate(phoneNumber) } returns null
        val saved = slot<UserProfile>()
        every { users.save(capture(saved)) } answers { firstArg() }
        val response = response()
        every { sessions.create(any(), any(), any(), true, false) } returns response

        val actual = service.verify(
            PhoneOtpVerifyRequest(nationalNumber, "123456", "Test Customer", rememberMe = false),
            "192.0.2.10",
        )

        assertEquals(response, actual)
        assertEquals(phoneNumber, saved.captured.phoneE164)
        assertEquals(null, saved.captured.email)
        assertEquals("Test Customer", saved.captured.displayName)
        verify { sessions.create(saved.captured, null, null, true, false) }
    }

    @Test
    fun `fifth wrong code burns challenge`() {
        every { provider.check(phoneNumber, "000000") } returns VerificationStatus.WRONG_CODE
        every { challenges.incrementAttempts(any(), authProperties.otpTtl) } returns 5

        assertThrows(TooManyCodeAttempts::class.java) {
            service.verify(PhoneOtpVerifyRequest(phoneNumber, "000000"), "192.0.2.10")
        }

        verify { provider.clear(phoneNumber) }
        verify { challenges.clear(any()) }
    }

    private fun response() = CustomerSessionResponse(
        accessToken = "access",
        expiresIn = 900,
        refreshToken = "refresh",
        user = CustomerSessionUser(
            UUID.randomUUID(), "Test Customer", null, phoneNumber, null,
            true, false, false, setOf("PHONE"),
        ),
    )
}
