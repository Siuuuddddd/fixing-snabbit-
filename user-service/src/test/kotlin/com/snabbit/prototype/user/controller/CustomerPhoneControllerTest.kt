package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.request.SaveCustomerPhoneRequest
import com.snabbit.prototype.user.exception.CustomerAccessDenied
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CustomerPhoneControllerTest {
    private val users = mockk<UserProfileRepository>()
    private val now = Instant.parse("2026-10-15T04:00:00Z")
    private val controller = CustomerPhoneController(users, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `owner can save a normalized Indian phone`() {
        val user = profile()
        every { users.findForUpdate(user.id) } returns user
        val localPhone = "98765" + "43210"

        val result = controller.save(user.id, user.id, SaveCustomerPhoneRequest(localPhone))

        assertEquals("+91" + localPhone, result.phoneNumber)
        assertEquals(now, user.updatedAt)
    }

    @Test
    fun `another customer cannot save the phone`() {
        val user = profile()

        assertThrows(CustomerAccessDenied::class.java) {
            controller.save(user.id, UUID.randomUUID(), SaveCustomerPhoneRequest("98765" + "43210"))
        }
    }

    private fun profile() = UserProfile(
        id = UUID.randomUUID(),
        phoneE164 = null,
        displayName = "Customer",
        status = UserStatus.ACTIVE,
        plusActive = false,
        plusExpiresAt = null,
        createdAt = now,
        updatedAt = now.minusSeconds(60),
    )
}
