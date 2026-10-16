package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.dto.response.HouseholdSnapshotResponse
import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerCompletionCountSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerOptionEvaluationResponse
import com.snabbit.prototype.booking.dto.response.WorkerOptionVisitEvaluationResponse
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.matching.MatchingClients
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerOptionsServiceTest {
    private val bookings = mockk<BookingRepository>()
    private val holds = mockk<SlotHoldRepository>(relaxed = true)
    private val catalog = mockk<ServiceCatalogService>()
    private val clients = mockk<MatchingClients>()
    private val completions = mockk<BookingCompletionQueryService>()
    private val now = Instant.parse("2026-10-14T06:00:00Z")
    private val service = WorkerOptionsService(
        bookings,
        holds,
        catalog,
        clients,
        completions,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val customerId = UUID.randomUUID()
    private val bookingId = UUID.randomUUID()

    @Test
    fun `returns favourites followed by every available worker in authoritative order`() = runBlocking {
        val favourites = List(3) { UUID.randomUUID() }
        val nearVeteran = UUID.randomUUID()
        val farVeteran = UUID.randomUUID()
        val thirdVeteran = UUID.randomUUID()
        stubBooking(favourites, plusActive = true)
        val request = slot<com.snabbit.prototype.booking.dto.request.WorkerOptionsQueryRequest>()
        coEvery { clients.workerOptions(capture(request)) } returns listOf(
            candidate(favourites[1], "EXPERT", BigDecimal("2.0"), favourite = true),
            candidate(favourites[0], "EXPERT", BigDecimal("1.0"), favourite = true),
            candidate(favourites[2], "VETERAN", BigDecimal("0.5"), favourite = true),
            candidate(farVeteran, "VETERAN", BigDecimal("3.0"), rank = BigDecimal("4.9")),
            candidate(nearVeteran, "VETERAN", BigDecimal("1.0"), rank = BigDecimal("4.5")),
            candidate(thirdVeteran, "VETERAN", BigDecimal("4.0"), rank = BigDecimal("5.0")),
        )

        val result = service.options(bookingId, customerId)

        assertEquals(favourites.toSet(), result.favourites.map { it.workerId }.toSet())
        assertEquals(setOf(thirdVeteran, farVeteran, nearVeteran), result.veterans.map { it.workerId }.toSet())
        assertFalse(result.veterans.any { it.workerId in favourites })
        assertEquals(favourites, request.captured.favouriteWorkerIds)
    }

    @Test
    fun `non Plus customer receives no favourites`() = runBlocking {
        val storedFavourites = List(3) { UUID.randomUUID() }
        val veteran = UUID.randomUUID()
        stubBooking(storedFavourites, plusActive = false)
        val request = slot<com.snabbit.prototype.booking.dto.request.WorkerOptionsQueryRequest>()
        coEvery { clients.workerOptions(capture(request)) } returns listOf(candidate(veteran, "VETERAN", BigDecimal("1.0")))

        val result = service.options(bookingId, customerId)

        assertTrue(result.favourites.isEmpty())
        assertTrue(request.captured.favouriteWorkerIds.isEmpty())
        assertEquals(listOf(veteran), result.veterans.map { it.workerId })
    }

    @Test
    fun `incompatible favourite is excluded from available worker options`() = runBlocking {
        val favourite = UUID.randomUUID()
        stubBooking(listOf(favourite), plusActive = true)
        val visits = listOf(
            visitEvaluation(0, available = true),
            visitEvaluation(1, available = false, reasons = setOf("VISIT_UNAVAILABLE")),
        )
        coEvery { clients.workerOptions(any()) } returns listOf(
            candidate(
                favourite,
                "EXPERT",
                BigDecimal("1.0"),
                favourite = true,
                selectable = false,
                reasons = setOf("PROFESSIONAL_REQUIREMENT_NOT_MET", "PARTIAL_OR_NO_AVAILABILITY"),
                visits = visits,
            ),
        )

        val result = service.options(bookingId, customerId)

        assertTrue(result.favourites.isEmpty())
        assertTrue(result.others.isEmpty())
    }

    @Test
    fun `booking owner is required`() {
        every { bookings.findById(bookingId) } returns Optional.of(booking())

        assertThrows(CustomerAccessDenied::class.java) {
            runBlocking { service.options(bookingId, UUID.randomUUID()) }
        }
    }

    private fun stubBooking(favourites: List<UUID>, plusActive: Boolean) {
        every { bookings.findById(bookingId) } returns Optional.of(booking())
        every { holds.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(hold(0), hold(1))
        every { catalog.requireActive(any()) } returns ServiceOfferingSnapshot(UUID.randomUUID(), "HOUSE_CLEANING", "House cleaning", 18_000)
        coEvery { clients.household(customerId) } returns household(favourites, plusActive)
        every { completions.completedCount(any()) } answers {
            WorkerCompletionCountSnapshot(firstArg(), 12)
        }
    }

    private fun booking() = Booking(
        id = bookingId,
        customerId = customerId,
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.MULTI_DAY,
        status = BookingStatus.AWAITING_WORKER,
        durationMinutes = 60,
        quotedTotalMinor = 36_000,
        serviceLatitude = BigDecimal("19.076090"),
        serviceLongitude = BigDecimal("72.877426"),
        createdAt = now,
        updatedAt = now,
    )

    private fun hold(index: Int) = SlotHold(
        id = UUID.randomUUID(),
        bookingId = bookingId,
        visitIndex = index,
        customerId = customerId,
        workerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        startsAt = now.plus(Duration.ofDays(index.toLong() + 1)),
        endsAt = now.plus(Duration.ofDays(index.toLong() + 1)).plusSeconds(3600),
        durationMinutes = 60,
        status = SlotHoldStatus.HELD,
        expiresAt = now.plusSeconds(600),
        createdAt = now,
    )

    private fun household(favourites: List<UUID>, plus: Boolean) = HouseholdSnapshotResponse(
        customerId,
        plus,
        favourites,
        false,
        null,
        null,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        false,
        emptyList(),
        emptyList(),
    )

    private fun candidate(
        id: UUID,
        tier: String,
        distance: BigDecimal,
        rank: BigDecimal = BigDecimal("4.8"),
        favourite: Boolean = false,
        selectable: Boolean = true,
        reasons: Set<String> = emptySet(),
        visits: List<WorkerOptionVisitEvaluationResponse> = listOf(visitEvaluation(0), visitEvaluation(1)),
    ) = WorkerOptionEvaluationResponse(
        id,
        "Worker",
        tier,
        BigDecimal("4.8"),
        100,
        8,
        distance,
        rank,
        if (tier == "EXPERT") 5_000 else 3_000,
        favourite,
        selectable,
        reasons,
        visits,
    )

    private fun visitEvaluation(
        index: Int,
        available: Boolean = true,
        reasons: Set<String> = emptySet(),
    ) = WorkerOptionVisitEvaluationResponse(
        index,
        now.plus(Duration.ofDays(index.toLong() + 1)),
        if (index == 0) 60 else 120,
        available,
        reasons,
        if (index == 0) 21_000 else 39_000,
    )
}
