package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.application.BookingLifecycleService
import com.snabbit.prototype.booking.application.BookingSnapshot
import com.snabbit.prototype.booking.application.CreateBookingCommand
import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.catalog.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class MatchAndHoldServiceTest {
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"), LocalTime.of(8, 0), LocalTime.of(19, 0),
        Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(30), 100,
    )
    private val windowPolicy = BookingWindowPolicy(properties, clock)
    private val service = MatchAndHoldService(
        mockk(), mockk<BookingLifecycleService>(), mockk(), properties, clock, windowPolicy,
    )

    @Test
    fun `Plus selects favourite worker continuously across every visit`() {
        val favourite = UUID.randomUUID()
        val other = UUID.randomUUID()
        val household = household(plusActive = true, favourites = listOf(favourite))
        val candidates = listOf(
            listOf(match(other), match(favourite)),
            listOf(match(favourite), match(other)),
            listOf(match(other), match(favourite)),
        )

        val selected = service.selectWorkers(candidates, household)

        assertEquals(listOf(favourite, favourite, favourite), selected)
    }

    @Test
    fun `falls back to best worker per visit when continuity is impossible`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val selected = service.selectWorkers(
            listOf(listOf(match(first)), listOf(match(second))),
            household(plusActive = true, favourites = emptyList()),
        )

        assertEquals(listOf(first, second), selected)
    }

    @Test
    fun `persists the authoritative requirements and selected worker quote`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val household = household(plusActive = false, favourites = emptyList()).copy(
            userId = customerId,
            hasPets = true,
            petDetails = "One dog",
            allergies = listOf("DUST"),
            workerConstraints = listOf("MASK_REQUIRED"),
        )
        val created = slot<CreateBookingCommand>()
        val matchRequest = slot<WorkerMatchRequest>()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId,
            "HOUSE_CLEANING",
            "House Cleaning",
            18_000,
        )
        coEvery { clients.household(customerId) } returns household
        coEvery { clients.matches(capture(matchRequest)) } returns listOf(match(workerId, 39_000))
        coEvery { clients.reservePass(customerId, any()) } answers {
            val request = secondArg<ReservePlusPassRequest>()
            PlusPassReservationResponse(null, request.bookingId, request.grossTotalMinor, 0, request.grossTotalMinor, null)
        }
        every { lifecycle.create(capture(created)) } answers {
            BookingSnapshot(
                bookingId = created.captured.bookingId,
                customerId = customerId,
                status = "AWAITING_WORKER",
                quotedTotalMinor = created.captured.quotedTotalMinor,
                grossTotalMinor = created.captured.grossTotalMinor,
                plusDiscountMinor = created.captured.plusDiscountMinor,
                plusPassId = created.captured.plusPassId,
                requirements = created.captured.requirements,
                visits = emptyList(),
            )
        }

        service.matchAndHold(
            MatchAndHoldCommand(
                customerId,
                serviceId,
                BookingType.SCHEDULED,
                Duration.ofHours(2),
                19.07609,
                72.877426,
                listOf(Instant.parse("2026-10-03T04:30:00Z")),
            ),
        )

        assertEquals(39_000, created.captured.quotedTotalMinor)
        assertEquals("HOUSE_CLEANING", matchRequest.captured.serviceCode)
        assertEquals(18_000, matchRequest.captured.basePricePerHourMinor)
        assertEquals("One dog", created.captured.requirements.petDetails)
        assertEquals(listOf("DUST"), created.captured.requirements.allergies)
        assertEquals(listOf("MASK_REQUIRED"), created.captured.requirements.workerConstraints)
    }

    @Test
    fun `applies reserved Plus pass and releases it if slot creation loses a race`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val passId = UUID.randomUUID()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.household(customerId) } returns household(false, emptyList()).copy(userId = customerId)
        coEvery { clients.matches(any()) } returns listOf(match(workerId, 18_000))
        coEvery { clients.reservePass(customerId, any()) } answers {
            val request = secondArg<ReservePlusPassRequest>()
            PlusPassReservationResponse(passId, request.bookingId, 18_000, 3_000, 15_000, request.holdUntil)
        }
        every { lifecycle.create(any()) } throws com.snabbit.prototype.booking.support.SlotUnavailable("lost race")
        coEvery { clients.releasePass(customerId, any()) } returns Unit

        assertThrows<com.snabbit.prototype.booking.support.SlotUnavailable> {
            service.matchAndHold(
                MatchAndHoldCommand(
                    customerId, serviceId, BookingType.SCHEDULED, Duration.ofHours(1),
                    19.07609, 72.877426, listOf(Instant.parse("2026-10-03T04:30:00Z")),
                ),
            )
        }

        coVerify(exactly = 1) { clients.releasePass(customerId, any()) }
    }

    @Test
    fun `availability identifies the visit customer must replace without creating a hold`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val availableStart = Instant.parse("2026-10-03T04:30:00Z")
        val unavailableStart = Instant.parse("2026-10-04T04:30:00Z")
        val workerId = UUID.randomUUID()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.household(customerId) } returns household(false, emptyList()).copy(userId = customerId)
        coEvery { clients.matches(any()) } answers {
            if (firstArg<WorkerMatchRequest>().startsAt == availableStart) listOf(match(workerId, 21_000)) else emptyList()
        }

        val result = service.availability(
            MatchAndHoldCommand(
                customerId,
                serviceId,
                BookingType.MULTI_DAY,
                Duration.ofHours(1),
                19.07609,
                72.877426,
                listOf(availableStart, unavailableStart),
            ),
        )

        assertEquals(false, result.allVisitsAvailable)
        assertEquals(true, result.visits[0].available)
        assertEquals(21_000, result.visits[0].lowestPriceMinor)
        assertEquals(false, result.visits[1].available)
        assertEquals(null, result.visits[1].lowestPriceMinor)
        coVerify(exactly = 0) { clients.reservePass(any(), any()) }
        io.mockk.verify(exactly = 0) { lifecycle.create(any()) }
    }

    private fun household(plusActive: Boolean, favourites: List<UUID>) = HouseholdSnapshotResponse(
        userId = UUID.randomUUID(),
        plusActive = plusActive,
        favouriteWorkerIds = favourites,
        hasPets = false,
        petDetails = null,
        foodContext = null,
        utensilPreferences = emptyList(),
        preferredLanguages = emptyList(),
        allergies = emptyList(),
        religiousFoodRestrictions = emptyList(),
        accessibilityNeeds = emptyList(),
        workerConstraints = emptyList(),
        heavyLiftingRequired = false,
        disallowedChemicals = emptyList(),
        requiredEquipment = emptyList(),
    )

    private fun match(workerId: UUID, priceMinor: Long = 10_000) = WorkerMatchResponse(
        workerId,
        "Worker",
        "STANDARD",
        BigDecimal("4.50"),
        BigDecimal.ONE,
        false,
        priceMinor,
    )
}
