package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.dto.request.CreateBookingCommand
import com.snabbit.prototype.booking.dto.request.MatchAndHoldCommand
import com.snabbit.prototype.booking.dto.request.ReservePlusPassRequest
import com.snabbit.prototype.booking.dto.request.WorkerMatchRequest
import com.snabbit.prototype.booking.dto.response.AddressSnapshotResponse
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.HouseholdSnapshotResponse
import com.snabbit.prototype.booking.dto.response.PlusPassReservationResponse
import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerMatchResponse
import com.snabbit.prototype.booking.exception.SlotUnavailable
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.PlanValidationException
import com.snabbit.prototype.booking.service.BookingLifecycleService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

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
    fun `validates configured visit counts at one minimum maximum and maximum plus one`() {
        val starts = (0 until properties.maxMultiDayVisits + 1).map { day ->
            Instant.parse("2026-10-02T05:30:00Z").plus(Duration.ofDays(day.toLong()))
        }

        assertThrows<BookingRuleViolation> { service.validateRequest(command(starts.take(1))) }
        service.validateRequest(command(starts.take(properties.minMultiDayVisits)))
        service.validateRequest(command(starts.take(properties.maxMultiDayVisits)))
        assertThrows<BookingRuleViolation> {
            service.validateRequest(command(starts.take(properties.maxMultiDayVisits + 1)))
        }
    }

    @Test
    fun `duplicate local dates return an indexed error for every duplicate visit`() {
        val first = Instant.parse("2026-10-03T04:30:00Z")
        val second = Instant.parse("2026-10-03T06:30:00Z")

        val error = assertThrows<PlanValidationException> {
            service.validateRequest(command(listOf(first, second)))
        }

        assertEquals(listOf(0, 1), error.visitErrors.map { it.visitIndex })
    }

    @Test
    fun `persists the authoritative requirements and selected worker quote`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val household = household(plusActive = false, favourites = emptyList()).copy(
            userId = customerId,
            hasPets = true,
            petDetails = "One dog",
            allergies = listOf("DUST"),
            religiousFoodRestrictions = listOf("NO_PORK"),
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
        coEvery { clients.address(customerId, addressId) } returns address(customerId, addressId)
        coEvery { clients.matches(capture(matchRequest)) } returns listOf(match(workerId, 39_000))
        coEvery { clients.reservePass(customerId, any()) } answers {
            val request = secondArg<ReservePlusPassRequest>()
            PlusPassReservationResponse(null, request.bookingId, request.grossTotalMinor, 0, request.grossTotalMinor, null)
        }
        every { lifecycle.findIdempotent(customerId, any(), any()) } returns null
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
                addressId,
                listOf(Instant.parse("2026-10-03T04:30:00Z")),
                "booking-request-001",
            ),
        )

        assertEquals(39_000, created.captured.quotedTotalMinor)
        assertEquals("HOUSE_CLEANING", matchRequest.captured.serviceCode)
        assertEquals(18_000, matchRequest.captured.basePricePerHourMinor)
        assertEquals(listOf("DUST"), matchRequest.captured.allergies)
        assertEquals(listOf("NO_PORK"), matchRequest.captured.religiousFoodRestrictions)
        assertEquals("One dog", created.captured.requirements.petDetails)
        assertEquals(listOf("DUST"), created.captured.requirements.allergies)
        assertEquals(listOf("MASK_REQUIRED"), created.captured.requirements.workerConstraints)
        assertEquals(addressId, created.captured.serviceAddressId)
        assertEquals("10 Test Street", created.captured.serviceAddressLine)
        assertEquals(BigDecimal("19.07609"), created.captured.serviceLatitude)
        assertEquals(19.07609, matchRequest.captured.latitude)
        assertEquals("booking-request-001", created.captured.idempotencyKey)
        assertEquals(64, created.captured.requestFingerprint?.length)
        assertEquals(
            UUID.nameUUIDFromBytes("$customerId:booking-request-001".toByteArray()),
            created.captured.bookingId,
        )
    }

    @Test
    fun `applies reserved Plus pass and releases it if slot creation loses a race`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val passId = UUID.randomUUID()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.household(customerId) } returns household(false, emptyList()).copy(userId = customerId)
        coEvery { clients.address(customerId, addressId) } returns address(customerId, addressId)
        coEvery { clients.matches(any()) } returns listOf(match(workerId, 18_000))
        coEvery { clients.reservePass(customerId, any()) } answers {
            val request = secondArg<ReservePlusPassRequest>()
            PlusPassReservationResponse(passId, request.bookingId, 18_000, 3_000, 15_000, request.holdUntil)
        }
        every { lifecycle.findIdempotent(customerId, any(), any()) } returns null
        every { lifecycle.create(any()) } throws com.snabbit.prototype.booking.exception.SlotUnavailable("lost race")
        coEvery { clients.releasePass(customerId, any()) } returns Unit

        assertThrows<com.snabbit.prototype.booking.exception.SlotUnavailable> {
            service.matchAndHold(
                MatchAndHoldCommand(
                    customerId, serviceId, BookingType.SCHEDULED, Duration.ofHours(1),
                    addressId, listOf(Instant.parse("2026-10-03T04:30:00Z")), "booking-request-002",
                ),
            )
        }

        coVerify(exactly = 1) { clients.releasePass(customerId, any()) }
    }

    @Test
    fun `replays an existing booking without matching or reserving another pass`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val existing = bookingSnapshot(customerId)
        every { lifecycle.findIdempotent(customerId, "booking-request-003", any()) } returns existing

        val result = service.matchAndHold(
            MatchAndHoldCommand(
                customerId,
                UUID.randomUUID(),
                BookingType.SCHEDULED,
                Duration.ofHours(1),
                UUID.randomUUID(),
                listOf(Instant.parse("2026-10-03T04:30:00Z")),
                "booking-request-003",
            ),
        )

        assertEquals(existing, result)
        verify(exactly = 0) { catalog.requireActive(any()) }
        coVerify(exactly = 0) { clients.household(any()) }
        coVerify(exactly = 0) { clients.reservePass(any(), any()) }
        verify(exactly = 0) { lifecycle.create(any()) }
    }

    @Test
    fun `concurrent replay keeps the winning Plus pass reservation`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val passId = UUID.randomUUID()
        val recovered = bookingSnapshot(customerId, passId)
        var idempotencyLookups = 0
        every { lifecycle.findIdempotent(customerId, "booking-request-004", any()) } answers {
            if (idempotencyLookups++ == 0) null else recovered
        }
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.household(customerId) } returns household(true, emptyList()).copy(userId = customerId)
        coEvery { clients.address(customerId, addressId) } returns address(customerId, addressId)
        coEvery { clients.matches(any()) } returns listOf(match(workerId, 18_000))
        coEvery { clients.reservePass(customerId, any()) } answers {
            val request = secondArg<ReservePlusPassRequest>()
            PlusPassReservationResponse(passId, request.bookingId, 18_000, 3_000, 15_000, request.holdUntil)
        }
        every { lifecycle.create(any()) } throws com.snabbit.prototype.booking.exception.SlotUnavailable("lost race")

        val result = service.matchAndHold(
            MatchAndHoldCommand(
                customerId, serviceId, BookingType.SCHEDULED, Duration.ofHours(1),
                addressId, listOf(Instant.parse("2026-10-03T04:30:00Z")), "booking-request-004",
            ),
        )

        assertEquals(recovered, result)
        assertEquals(2, idempotencyLookups)
        coVerify(exactly = 0) { clients.releasePass(any(), any()) }
    }

    @Test
    fun `availability identifies the visit customer must replace without creating a hold`() = runBlocking {
        val clients = mockk<MatchingClients>()
        val lifecycle = mockk<BookingLifecycleService>()
        val catalog = mockk<ServiceCatalogService>()
        val service = MatchAndHoldService(clients, lifecycle, catalog, properties, clock, windowPolicy)
        val customerId = UUID.randomUUID()
        val serviceId = UUID.randomUUID()
        val addressId = UUID.randomUUID()
        val availableStart = Instant.parse("2026-10-03T04:30:00Z")
        val unavailableStart = Instant.parse("2026-10-04T04:30:00Z")
        val workerId = UUID.randomUUID()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.household(customerId) } returns household(false, emptyList()).copy(userId = customerId)
        coEvery { clients.address(customerId, addressId) } returns address(customerId, addressId)
        coEvery { clients.matches(any()) } answers {
            if (firstArg<WorkerMatchRequest>().startsAt == availableStart) listOf(match(workerId, 21_000)) else emptyList()
        }

        val result = service.availability(
            MatchAndHoldCommand(
                customerId,
                serviceId,
                BookingType.MULTI_DAY,
                Duration.ofHours(1),
                addressId,
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

    private fun address(userId: UUID, addressId: UUID) = AddressSnapshotResponse(
        addressId,
        userId,
        "Home",
        "10 Test Street",
        "Mumbai",
        "400001",
        BigDecimal("19.07609"),
        BigDecimal("72.877426"),
        true,
    )

    private fun bookingSnapshot(customerId: UUID, passId: UUID? = null) = BookingSnapshot(
        bookingId = UUID.randomUUID(),
        customerId = customerId,
        status = "AWAITING_WORKER",
        quotedTotalMinor = if (passId == null) 18_000 else 15_000,
        grossTotalMinor = 18_000,
        plusDiscountMinor = if (passId == null) 0 else 3_000,
        plusPassId = passId,
        requirements = BookingRequirements(),
        visits = emptyList(),
    )

    private fun command(startsAt: List<Instant>) = MatchAndHoldCommand(
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.MULTI_DAY,
        duration = Duration.ofHours(1),
        addressId = UUID.randomUUID(),
        startsAt = startsAt,
    )
}
