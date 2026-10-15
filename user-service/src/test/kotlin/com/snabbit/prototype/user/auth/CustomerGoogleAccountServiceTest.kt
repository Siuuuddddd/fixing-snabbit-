package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.exception.CustomerAccountConflict
import com.snabbit.prototype.user.exception.CustomerAccountDisabled
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomerGoogleAccountServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val now = Instant.parse("2026-10-15T04:00:00Z")
    private val service = CustomerGoogleAccountService(users, Clock.fixed(now, ZoneOffset.UTC))
    private val email = "customer" + "@" + "example.test"

    @Test
    fun `first sign-in creates active customer without a phone or onboarding gate`() {
        every { users.findByGoogleSub("google-subject") } returns null
        every { users.findByEmailForUpdate(email) } returns null
        every { users.save(any()) } answers { firstArg() }

        val account = service.resolve(identity())

        assertTrue(account.newUser)
        assertEquals(UserStatus.ACTIVE, account.profile.status)
        assertNull(account.profile.phoneE164)
        assertEquals("google-subject", account.profile.googleSub)
        assertEquals(now, account.profile.lastLoginAt)
    }

    @Test
    fun `repeat sign-in resolves by subject and refreshes verified profile fields`() {
        val existing = profile().apply { googleSub = "google-subject" }
        every { users.findByGoogleSub("google-subject") } returns existing

        val account = service.resolve(identity(name = "Updated Name"))

        assertEquals(existing.id, account.profile.id)
        assertEquals("Updated Name", existing.displayName)
        verify(exactly = 0) { users.findByEmailForUpdate(any()) }
    }

    @Test
    fun `verified email links a pre-provisioned customer once`() {
        val existing = profile()
        every { users.findByGoogleSub("google-subject") } returns null
        every { users.findByEmailForUpdate(email) } returns existing

        val account = service.resolve(identity())

        assertEquals(existing.id, account.profile.id)
        assertEquals("google-subject", existing.googleSub)
    }

    @Test
    fun `disabled and conflicting accounts are rejected`() {
        val disabled = profile(UserStatus.SUSPENDED).apply { googleSub = "google-subject" }
        every { users.findByGoogleSub("google-subject") } returns disabled
        assertThrows(CustomerAccountDisabled::class.java) { service.resolve(identity()) }

        val conflict = profile().apply { googleSub = "different-subject" }
        every { users.findByGoogleSub("google-subject") } returns null
        every { users.findByEmailForUpdate(email) } returns conflict
        assertThrows(CustomerAccountConflict::class.java) { service.resolve(identity()) }
    }

    private fun identity(name: String? = "Customer") =
        GoogleIdentity("google-subject", email, name, "https://images.example.test/profile")

    private fun profile(status: UserStatus = UserStatus.ACTIVE) = UserProfile(
        id = UUID.randomUUID(),
        phoneE164 = null,
        displayName = "Pre-provisioned",
        status = status,
        plusActive = false,
        plusExpiresAt = null,
        createdAt = now,
        updatedAt = now,
        email = email,
        emailVerified = true,
    )
}
