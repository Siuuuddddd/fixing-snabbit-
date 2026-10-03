package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusPassStatus
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.UserStatus
import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.repository.PlusSubscriptionRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import com.snabbit.prototype.user.events.UserOutboxWriter
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PlusServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val subscriptions = mockk<PlusSubscriptionRepository>()
    private val passes = mockk<PlusPassRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = PlusService(
        users, subscriptions, passes, Clock.fixed(now, ZoneOffset.UTC), mockk<UserOutboxWriter>(relaxed = true),
    )

    @Test
    fun `active member reserves an eligible pass and receives server-side discount`() {
        val user = user()
        val bookingId = UUID.randomUUID()
        val pass = pass(user.id)
        val holdUntil = now.plusSeconds(300)
        every { passes.findByBookingForUpdate(bookingId) } returns null
        every { users.findForUpdate(user.id) } returns user
        every {
            passes.findEligibleForUpdate(user.id, "HOUSE_CLEANING", PlusPassStatus.AVAILABLE, holdUntil)
        } returns listOf(pass)

        val reservation = service.reservePass(user.id, bookingId, "house_cleaning", 18_000, holdUntil)

        assertEquals(pass.id, reservation.passId)
        assertEquals(3_000, reservation.discountMinor)
        assertEquals(15_000, reservation.netTotalMinor)
        assertEquals(PlusPassStatus.HELD, pass.status)
        assertEquals(bookingId, pass.heldForBookingId)
    }

    @Test
    fun `reservation replay returns the same held pass without taking another`() {
        val user = user()
        val bookingId = UUID.randomUUID()
        val pass = pass(user.id).apply { reserve(bookingId, now.plusSeconds(300), now) }
        every { passes.findByBookingForUpdate(bookingId) } returns pass

        val replay = service.reservePass(user.id, bookingId, "HOUSE_CLEANING", 18_000, now.plusSeconds(300))

        assertEquals(pass.id, replay.passId)
        verify(exactly = 0) { users.findForUpdate(any()) }
        verify(exactly = 0) { passes.findEligibleForUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `pass discount never reduces a booking below one rupee`() {
        val user = user()
        val bookingId = UUID.randomUUID()
        val pass = pass(user.id)
        val holdUntil = now.plusSeconds(300)
        every { passes.findByBookingForUpdate(bookingId) } returns null
        every { users.findForUpdate(user.id) } returns user
        every {
            passes.findEligibleForUpdate(user.id, "HOUSE_CLEANING", PlusPassStatus.AVAILABLE, holdUntil)
        } returns listOf(pass)

        val reservation = service.reservePass(user.id, bookingId, "HOUSE_CLEANING", 100, holdUntil)

        assertEquals(null, reservation.passId)
        assertEquals(0, reservation.discountMinor)
        assertEquals(100, reservation.netTotalMinor)
        assertEquals(PlusPassStatus.AVAILABLE, pass.status)
    }

    @Test
    fun `booking confirmation consumes a held pass using event occurrence time`() {
        val user = user()
        val bookingId = UUID.randomUUID()
        val pass = pass(user.id).apply { reserve(bookingId, now.plusSeconds(300), now) }
        every { passes.findByBookingForUpdate(bookingId) } returns pass

        service.consumePass(bookingId, now.plusSeconds(299))

        assertEquals(PlusPassStatus.USED, pass.status)
        assertEquals(now.plusSeconds(299), pass.usedAt)
    }

    @Test
    fun `confirmed cancellation restores consumed pass`() {
        val user = user()
        val bookingId = UUID.randomUUID()
        val pass = pass(user.id).apply {
            reserve(bookingId, now.plusSeconds(300), now)
            consume(now.plusSeconds(120))
        }
        every { passes.findByBookingForUpdate(bookingId) } returns pass

        service.restorePass(bookingId)

        assertEquals(PlusPassStatus.AVAILABLE, pass.status)
        assertEquals(null, pass.heldForBookingId)
        assertEquals(null, pass.usedAt)
    }

    @Test
    fun `early Plus renewal extends current expiry instead of losing purchased days`() {
        val user = user().apply { plusExpiresAt = now.plusSeconds(10 * 86_400L) }
        every { users.findForUpdate(user.id) } returns user
        every { subscriptions.findBySourceReference("plus-purchase:${testPurchaseId}") } returns null
        every { subscriptions.save(any()) } answers { firstArg() }
        every { passes.saveAll(any<List<PlusPass>>()) } answers { firstArg() }
        every { users.findById(user.id) } returns java.util.Optional.of(user)
        every { passes.countByUserIdAndStatusAndExpiresAtAfter(user.id, PlusPassStatus.AVAILABLE, now) } returns 4

        service.activatePurchase(user.id, testPurchaseId, now, 30, 4, 3_000, null)

        assertEquals(now.plusSeconds(40 * 86_400L), user.plusExpiresAt)
        verify(exactly = 1) {
            subscriptions.save(match { it.startsAt == now.plusSeconds(10 * 86_400L) })
        }
    }

    private val testPurchaseId = UUID.randomUUID()

    private fun user() = UserProfile(
        UUID.randomUUID(), "+919999999999", "Customer", UserStatus.ACTIVE,
        true, now.plusSeconds(86_400), now, now,
    )

    private fun pass(userId: UUID) = PlusPass(
        UUID.randomUUID(), UUID.randomUUID(), userId, "HOUSE_CLEANING", 3_000,
        PlusPassStatus.AVAILABLE, null, null, null, now.plusSeconds(86_400), now,
    )
}
