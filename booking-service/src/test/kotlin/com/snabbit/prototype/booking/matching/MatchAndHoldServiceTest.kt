package com.snabbit.prototype.booking.matching

import com.snabbit.prototype.booking.application.BookingLifecycleService
import com.snabbit.prototype.booking.application.BookingSnapshot
import com.snabbit.prototype.booking.application.CreateBookingCommand
import com.snabbit.prototype.booking.domain.BookingType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MatchAndHoldServiceTest {
    private val service = MatchAndHoldService(mockk(), mockk<BookingLifecycleService>())

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
        val service = MatchAndHoldService(clients, lifecycle)
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
        coEvery { clients.household(customerId) } returns household
        coEvery { clients.matches(any()) } returns listOf(match(workerId, 39_000))
        every { lifecycle.create(capture(created)) } answers {
            BookingSnapshot(
                UUID.randomUUID(),
                customerId,
                "AWAITING_WORKER",
                created.captured.quotedTotalMinor,
                created.captured.requirements,
                emptyList(),
            )
        }

        service.matchAndHold(
            MatchAndHoldCommand(
                customerId,
                serviceId,
                "HOUSE_CLEANING",
                BookingType.SCHEDULED,
                Duration.ofHours(2),
                19.07609,
                72.877426,
                listOf(Instant.parse("2026-10-03T04:30:00Z")),
            ),
        )

        assertEquals(39_000, created.captured.quotedTotalMinor)
        assertEquals("One dog", created.captured.requirements.petDetails)
        assertEquals(listOf("DUST"), created.captured.requirements.allergies)
        assertEquals(listOf("MASK_REQUIRED"), created.captured.requirements.workerConstraints)
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
