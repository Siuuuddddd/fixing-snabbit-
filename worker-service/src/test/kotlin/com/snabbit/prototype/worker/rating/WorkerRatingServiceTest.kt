package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.dto.response.BookingRatingContext
import com.snabbit.prototype.worker.dto.response.BookingRatingVisit
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import com.snabbit.prototype.worker.exception.WorkerRatingRejected
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorkerRatingServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val ratings = mockk<WorkerRatingRepository>()
    private val outbox = mockk<WorkerOutboxWriter>(relaxed = true)
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val transactions = WorkerRatingTransaction(
        workers, ratings, Clock.fixed(now, ZoneOffset.UTC), outbox,
        WorkerRatingProperties(updateWindow = Duration.ofHours(24)),
    )

    @Test
    fun `rating updates worker aggregate once and replay is idempotent`() {
        val worker = worker()
        val bookingId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val saved = slot<WorkerRating>()
        every { workers.findForUpdate(worker.id) } returns worker
        every { ratings.findByBookingIdAndVisitIndex(bookingId, 0) } returns null andThenAnswer { saved.captured }
        every { ratings.save(capture(saved)) } answers { firstArg() }

        val first = transactions.record(bookingId, 0, worker.id, customerId, 5, " Great ")
        val replay = transactions.record(bookingId, 0, worker.id, customerId, 5, "Great")

        assertEquals(BigDecimal("4.73"), first.workerAverageRating)
        assertEquals(first, replay)
        assertEquals(52, worker.ratingSum)
        assertEquals(11, worker.ratingCount)
        verify(exactly = 1) { ratings.save(any()) }
        verify(exactly = 1) {
            outbox.append(worker.id, "WorkerRated", match { "stars" !in it.keys && "ratingId" in it.keys }, now)
        }
    }

    @Test
    fun `different rating updates aggregate inside update window`() {
        val worker = worker()
        val existing = WorkerRating(
            UUID.randomUUID(), UUID.randomUUID(), 0, worker.id, UUID.randomUUID(), 3, null,
            now.minusSeconds(3_600), now.minusSeconds(3_600),
        )
        every { workers.findForUpdate(worker.id) } returns worker
        every { ratings.findByBookingIdAndVisitIndex(existing.bookingId, 0) } returns existing

        val updated = transactions.record(existing.bookingId, 0, worker.id, existing.customerId, 5, "better")

        assertEquals(49, worker.ratingSum)
        assertEquals(10, worker.ratingCount)
        assertEquals(5, updated.stars)
        assertEquals("better", updated.comment)
    }

    @Test
    fun `different rating is rejected after update window`() {
        val worker = worker()
        val existing = WorkerRating(
            UUID.randomUUID(), UUID.randomUUID(), 0, worker.id, UUID.randomUUID(), 3, null,
            now.minusSeconds(25 * 3_600), now.minusSeconds(25 * 3_600),
        )
        every { workers.findForUpdate(worker.id) } returns worker
        every { ratings.findByBookingIdAndVisitIndex(existing.bookingId, 0) } returns existing

        val error = assertThrows(WorkerRatingRejected::class.java) {
            transactions.record(existing.bookingId, 0, worker.id, existing.customerId, 5, null)
        }

        assertEquals("RATING_UPDATE_WINDOW_EXPIRED", error.errorCode)
        assertEquals(47, worker.ratingSum)
    }

    @Test
    fun `customer can rate only assigned worker of completed visit`() {
        val bookingClient = mockk<BookingRatingClient>()
        val tx = mockk<WorkerRatingTransaction>()
        val service = WorkerRatingService(bookingClient, tx)
        val bookingId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val assignedWorker = UUID.randomUUID()
        every { bookingClient.context(bookingId) } returns BookingRatingContext(
            bookingId,
            customerId,
            listOf(BookingRatingVisit(0, assignedWorker, true)),
        )

        assertThrows(WorkerRatingRejected::class.java) {
            service.rate(customerId, UUID.randomUUID(), bookingId, 0, 5, null)
        }

        verify(exactly = 0) { tx.record(any(), any(), any(), any(), any(), any()) }
    }

    private fun worker() = WorkerProfile(
        UUID.randomUUID(),
        "+919900000001",
        "Asha",
        WorkerTier.VETERAN,
        WorkerStatus.ACTIVE,
        BigDecimal("19.076090"),
        BigDecimal("72.877426"),
        BigDecimal("15"),
        47,
        10,
        now,
        now,
    )
}
