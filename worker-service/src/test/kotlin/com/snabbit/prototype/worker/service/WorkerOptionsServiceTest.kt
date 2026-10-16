package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerAvailabilityRule
import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerSkill
import com.snabbit.prototype.worker.domain.WorkerSkillId
import com.snabbit.prototype.worker.domain.enums.AvailabilityPeriod
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.dto.request.WorkerOptionVisitRequest
import com.snabbit.prototype.worker.dto.request.WorkerOptionsCommand
import com.snabbit.prototype.worker.rating.WorkerRankingPolicy
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRuleRepository
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerSkillRepository
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerOptionsServiceTest {
    private val profiles = mockk<WorkerProfileRepository>()
    private val skills = mockk<WorkerSkillRepository>()
    private val availability = mockk<WorkerAvailabilityRuleRepository>()
    private val compatibility = mockk<WorkerCompatibilityRepository>()
    private val tiers = mockk<WorkerTierPolicy> {
        every { currentTier(any()) } answers { (firstArg<WorkerProfile>()).tier }
        every { surchargeMinor(any()) } answers {
            when (firstArg<WorkerTier>()) {
                WorkerTier.STANDARD -> 0
                WorkerTier.VETERAN -> 3_000
                WorkerTier.EXPERT -> 5_000
            }
        }
        every { completedMonths(any()) } returns 18
    }
    private val ranking = mockk<WorkerRankingPolicy> {
        every { score(any()) } returns BigDecimal("4.8000")
    }
    private val service = WorkerOptionsService(
        profiles,
        skills,
        availability,
        compatibility,
        tiers,
        ranking,
        WorkerCompatibilityPolicy(),
        WorkerLocationPolicy(),
    )
    private val firstStart = Instant.parse("2026-10-15T04:30:00Z")
    private val secondStart = Instant.parse("2026-10-16T06:30:00Z")

    @Test
    fun `keeps an incompatible favourite visible with reasons for every failed visit`() {
        val favourite = profile(WorkerTier.EXPERT, "Favourite Worker")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(favourite)
        every { profiles.findAllById(listOf(favourite.id)) } returns listOf(favourite)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(skill(favourite.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(workerCompatibility(favourite.id, acceptsPets = false))
        every { availability.findAllByWorkerIdIn(any()) } returns listOf(rule(favourite.id))

        val option = service.options(command(favourite.id, hasPets = true)).single()

        assertTrue(option.favourite)
        assertFalse(option.selectable)
        assertTrue("PETS_NOT_ACCEPTED" in option.reasonCodes)
        assertTrue("PARTIAL_OR_NO_AVAILABILITY" in option.reasonCodes)
        assertEquals(listOf(false, false), option.visits.map { it.available })
        assertTrue("VISIT_UNAVAILABLE" in option.visits[1].reasonCodes)
    }

    @Test
    fun `returns selectable workers across tiers with per visit prices`() {
        val favourite = profile(WorkerTier.VETERAN, "Favourite Veteran")
        val veteran = profile(WorkerTier.VETERAN, "Nearby Veteran")
        val standard = profile(WorkerTier.STANDARD, "Standard Worker")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(favourite, veteran, standard)
        every { profiles.findAllById(listOf(favourite.id)) } returns listOf(favourite)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns
            listOf(skill(favourite.id), skill(veteran.id), skill(standard.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(
            workerCompatibility(favourite.id), workerCompatibility(veteran.id), workerCompatibility(standard.id),
        )
        every { availability.findAllByWorkerIdIn(any()) } returns listOf(
            rule(favourite.id, LocalTime.MIN, LocalTime.MAX),
            rule(veteran.id, LocalTime.MIN, LocalTime.MAX),
            rule(standard.id, LocalTime.MIN, LocalTime.MAX),
        )

        val options = service.options(command(favourite.id, hasPets = false))

        assertEquals(setOf(favourite.id, veteran.id, standard.id), options.map { it.workerId }.toSet())
        val veteranOption = options.single { it.workerId == veteran.id }
        assertTrue(veteranOption.selectable)
        assertEquals(listOf(21_000L, 39_000L), veteranOption.visits.map { it.priceMinor })
        assertEquals("Nearby", veteranOption.firstName)
    }

    private fun command(favouriteId: UUID, hasPets: Boolean) = WorkerOptionsCommand(
        serviceCode = "HOUSE_CLEANING",
        basePricePerHourMinor = 18_000,
        visits = listOf(
            WorkerOptionVisitRequest(0, firstStart, 60),
            WorkerOptionVisitRequest(1, secondStart, 120),
        ),
        latitude = 19.076090,
        longitude = 72.877426,
        maxDistanceKm = 15.0,
        hasPets = hasPets,
        foodContext = "VEGETARIAN",
        utensilPreferences = emptyList(),
        preferredLanguages = listOf("HINDI"),
        allergies = emptyList(),
        religiousFoodRestrictions = emptyList(),
        workerConstraints = emptyList(),
        heavyLiftingRequired = false,
        disallowedChemicals = emptyList(),
        requiredEquipment = listOf("MOP"),
        accessibilityNeeds = emptyList(),
        favouriteWorkerIds = listOf(favouriteId),
    )

    private fun rule(
        workerId: UUID,
        firstStart: LocalTime = LocalTime.of(8, 0),
        lastStart: LocalTime = LocalTime.of(11, 45),
    ) = WorkerAvailabilityRule(workerId, AvailabilityPeriod.MORNING, firstStart, lastStart)

    private fun profile(tier: WorkerTier, name: String) = WorkerProfile(
        id = UUID.randomUUID(),
        phoneE164 = "+919000000001",
        displayName = name,
        tier = tier,
        status = WorkerStatus.ACTIVE,
        currentLatitude = BigDecimal("19.076090"),
        currentLongitude = BigDecimal("72.877426"),
        serviceRadiusKm = BigDecimal("15.00"),
        ratingSum = 48,
        ratingCount = 10,
        createdAt = firstStart,
        updatedAt = firstStart,
    )

    private fun skill(workerId: UUID) = WorkerSkill(
        WorkerSkillId(workerId, "HOUSE_CLEANING"),
        basePricePerHourMinor = 18_000,
        enabled = true,
    )

    private fun available(workerId: UUID, date: LocalDate) = WorkerAvailability(
        id = UUID.randomUUID(),
        workerId = workerId,
        serviceDate = date,
        availableFrom = LocalTime.of(8, 0),
        availableUntil = LocalTime.of(19, 0),
        status = AvailabilityStatus.AVAILABLE,
    )

    private fun workerCompatibility(workerId: UUID, acceptsPets: Boolean = true) = WorkerCompatibility(
        workerId = workerId,
        acceptsPets = acceptsPets,
        languages = listOf("HINDI"),
        acceptedFoodContexts = listOf("VEGETARIAN"),
        supportedUtensilPreferences = emptyList(),
        supportedWorkerConstraints = emptyList(),
        supportedAllergyConstraints = emptyList(),
        acceptedReligiousFoodRestrictions = emptyList(),
        canHeavyLift = true,
        availableEquipment = listOf("MOP"),
        avoidedChemicals = emptyList(),
        accessibilityCapabilities = emptyList(),
        updatedAt = firstStart,
    )
}
