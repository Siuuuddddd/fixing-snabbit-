package com.snabbit.prototype.user.rating

import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.UserStatus
import com.snabbit.prototype.user.repository.CustomerRatingRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import com.snabbit.prototype.user.events.UserOutboxWriter
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CustomerRatingServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val ratings = mockk<CustomerRatingRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val transactions = CustomerRatingTransaction(
        users, ratings, Clock.fixed(now, ZoneOffset.UTC), mockk<UserOutboxWriter>(relaxed = true),
    )

    @Test
    fun `rating updates customer aggregate once and replay is idempotent`() {
        val customer = customer()
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val saved = slot<CustomerRating>()
        every { users.findForUpdate(customer.id) } returns customer
        every { ratings.findByBookingIdAndVisitIndex(bookingId, 0) } returns null andThenAnswer { saved.captured }
        every { ratings.save(capture(saved)) } answers { firstArg() }

        val first = transactions.record(bookingId, 0, customer.id, workerId, 4, " Prepared ")
        val replay = transactions.record(bookingId, 0, customer.id, workerId, 4, "Prepared")

        assertEquals(BigDecimal("4.00"), first.customerAverageRating)
        assertEquals(first, replay)
        assertEquals(4, customer.ratingSum)
        assertEquals(1, customer.ratingCount)
        verify(exactly = 1) { ratings.save(any()) }
    }

    @Test
    fun `worker can rate only customer from own completed visit`() {
        val bookingClient = mockk<CustomerRatingBookingClient>()
        val tx = mockk<CustomerRatingTransaction>()
        val service = CustomerRatingService(bookingClient, tx)
        val bookingId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val assignedWorker = UUID.randomUUID()
        every { bookingClient.context(bookingId) } returns BookingRatingContext(
            bookingId,
            customerId,
            listOf(BookingRatingVisit(0, assignedWorker, false)),
        )

        assertThrows(CustomerRatingRejected::class.java) {
            service.rate(assignedWorker, customerId, bookingId, 0, 5, null)
        }

        verify(exactly = 0) { tx.record(any(), any(), any(), any(), any(), any()) }
    }

    private fun customer() = UserProfile(
        UUID.randomUUID(),
        "+919876543210",
        "Customer",
        UserStatus.ACTIVE,
        false,
        null,
        now,
        now,
    )
}
