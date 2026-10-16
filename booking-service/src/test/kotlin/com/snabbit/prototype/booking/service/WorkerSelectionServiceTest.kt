package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.dto.request.ApplyVisitWorkerSelectionCommand
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionVisitSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionsSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WorkerSelectionServiceTest {
    private val options = mockk<WorkerOptionsService>()
    private val lifecycle = mockk<BookingLifecycleService>()
    private val service = WorkerSelectionService(options, lifecycle)
    private val bookingId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()

    @Test
    fun `customer selection sends authoritative tier and visit prices to lifecycle`() = runBlocking {
        val veteran = candidate(UUID.randomUUID(), "VETERAN", 3_000)
        coEvery { options.options(bookingId, customerId, 0) } returns
            WorkerOptionsSnapshot(bookingId, 0, emptyList(), listOf(veteran))
        val command = slot<ApplyVisitWorkerSelectionCommand>()
        every { lifecycle.applyVisitWorkerSelection(capture(command)) } returns snapshot()

        service.select(bookingId, customerId, 0, veteran.workerId)

        assertEquals(veteran.workerId, command.captured.workerId)
        assertEquals("VETERAN", command.captured.tier)
        assertEquals(0, command.captured.visitIndex)
        assertEquals(21_000L, command.captured.grossVisitPriceMinor)
    }

    @Test
    fun `stale or unlisted worker is rejected after fresh option lookup`() {
        coEvery { options.options(bookingId, customerId, 0) } returns
            WorkerOptionsSnapshot(bookingId, 0, emptyList(), emptyList())

        assertThrows(BookingRuleViolation::class.java) {
            runBlocking { service.select(bookingId, customerId, 0, UUID.randomUUID()) }
        }
    }

    private fun candidate(
        workerId: UUID,
        tier: String,
        surcharge: Long,
        selectable: Boolean = true,
    ) = WorkerOptionSnapshot(
        workerId = workerId,
        firstName = "Worker",
        tier = tier,
        averageRating = BigDecimal("4.8"),
        ratingCount = 20,
        completedJobs = 30,
        tenureMonths = 8,
        distanceKm = BigDecimal("1.2"),
        tierSurchargeMinor = surcharge,
        bookingTotalMinor = 60_000,
        selectable = selectable,
        reasonCodes = if (selectable) emptySet() else setOf("VISIT_UNAVAILABLE"),
        visits = listOf(
            visit(0, 60, 21_000, selectable),
            visit(1, 120, 39_000, selectable),
        ),
    )

    private fun visit(index: Int, duration: Int, price: Long, available: Boolean) = WorkerOptionVisitSnapshot(
        index,
        LocalDate.of(2026, 10, 15 + index),
        LocalTime.of(10, 0),
        duration,
        available,
        if (available) emptySet() else setOf("VISIT_UNAVAILABLE"),
        price,
    )

    private fun snapshot() = mockk<BookingSnapshot>()
}
