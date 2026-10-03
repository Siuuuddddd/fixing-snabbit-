package com.snabbit.prototype.user.service

import com.snabbit.prototype.user.domain.UserAddress
import com.snabbit.prototype.user.dto.request.AddressCommand
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.AddressAccessDenied
import com.snabbit.prototype.user.exception.AddressRejected
import com.snabbit.prototype.user.location.AddressLocationVerifier
import com.snabbit.prototype.user.repository.UserAddressRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AddressServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val addresses = mockk<UserAddressRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val outbox = mockk<UserOutboxWriter>(relaxed = true)
    private val service = AddressService(
        users, addresses, clock,
        AddressWriteTransaction(users, addresses, outbox, clock), mockk<AddressLocationVerifier>(relaxed = true),
    )

    @Test
    fun `fresh accurate Mumbai location creates verified owned address`() {
        val userId = UUID.randomUUID()
        every { users.findForUpdate(userId) } returns mockk(relaxed = true)
        every { addresses.countByUserId(userId) } returns 0
        every { addresses.save(any()) } answers { firstArg() }

        val result = service.create(userId, command())

        assertEquals(userId, result.userId)
        assertEquals(true, result.verified)
        assertEquals(BigDecimal("19.076090"), result.latitude)
        assertEquals("Mumbai", result.city)
    }

    @Test
    fun `stale or inaccurate coordinates cannot become a verified address`() {
        val userId = UUID.randomUUID()
        every { users.findForUpdate(userId) } returns mockk(relaxed = true)
        every { addresses.countByUserId(userId) } returns 0

        assertThrows(AddressRejected::class.java) {
            service.create(
                userId,
                command().copy(locationAccuracyMeters = BigDecimal("101"), locationCapturedAt = now.minusSeconds(600)),
            )
        }

        verify(exactly = 0) { addresses.save(any()) }
    }

    @Test
    fun `internal lookup rejects an address owned by another customer`() {
        val ownerId = UUID.randomUUID()
        val requestedUserId = UUID.randomUUID()
        val address = address(ownerId)
        every { addresses.findById(address.id) } returns Optional.of(address)

        assertThrows(AddressAccessDenied::class.java) {
            service.requireOwnedVerified(requestedUserId, address.id)
        }
    }

    private fun command() = AddressCommand(
        "Home",
        "10 Test Street",
        "Mumbai",
        "400001",
        BigDecimal("19.07609"),
        BigDecimal("72.877426"),
        BigDecimal("20"),
        now.minusSeconds(30),
    )

    private fun address(userId: UUID) = UserAddress(
        UUID.randomUUID(),
        userId,
        "Home",
        "10 Test Street",
        "Mumbai",
        "400001",
        BigDecimal("19.076090"),
        BigDecimal("72.877426"),
        true,
        now,
    )
}
