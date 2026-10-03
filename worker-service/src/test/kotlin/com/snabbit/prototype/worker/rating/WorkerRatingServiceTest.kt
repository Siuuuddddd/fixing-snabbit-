package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.domain.WorkerStatus
import com.snabbit.prototype.worker.domain.WorkerTier
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import io.mockk.every
import io.mockk.mockk
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
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

class WorkerRatingServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val ratings = mockk<WorkerRatingRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val transactions = WorkerRatingTransaction(
        workers, ratings, Clock.fixed(now, ZoneOffset.UTC), mockk<WorkerOutboxWriter>(relaxed = true),
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
