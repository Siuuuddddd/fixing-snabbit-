package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerSkill
import com.snabbit.prototype.worker.domain.WorkerSkillId
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import com.snabbit.prototype.worker.repository.WorkerAvailabilityRepository
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
import org.junit.jupiter.api.Test

class WorkerMatchingServiceTest {
    private val profiles = mockk<WorkerProfileRepository>()
    private val skills = mockk<WorkerSkillRepository>()
    private val availability = mockk<WorkerAvailabilityRepository>()
    private val compatibility = mockk<WorkerCompatibilityRepository>()
    private val tierPolicy = mockk<WorkerTierPolicy> {
        every { currentTier(any()) } answers { (invocation.args[0] as WorkerProfile).tier }
        every { surchargeMinor(any()) } answers {
            when (invocation.args[0] as WorkerTier) {
                WorkerTier.STANDARD -> 0
                WorkerTier.VETERAN -> 3_000
                WorkerTier.EXPERT -> 5_000
            }
        }
    }
    private val service = WorkerMatchingService(profiles, skills, availability, compatibility, tierPolicy)
    private val start = Instant.parse("2026-10-03T04:30:00Z")

    @Test
    fun `Plus favourite continuity ranks compatible favourite first`() {
        val standard = profile(WorkerTier.STANDARD, "Standard")
        val favourite = profile(WorkerTier.VETERAN, "Favourite")
        stubCandidates(standard, favourite, acceptsPets = true)

        val matches = service.match(command(favourite.id, hasPets = true))

        assertEquals(favourite.id, matches.first().workerId)
        assertEquals(39_000, matches.first().priceMinor) // 2 * ₹180 + ₹30 tier charge
        assertEquals(true, matches.first().favourite)
    }

    @Test
    fun `household pet constraint removes incompatible favourite`() {
        val compatibleWorker = profile(WorkerTier.STANDARD, "Pet friendly")
        val incompatibleFavourite = profile(WorkerTier.EXPERT, "No pets")
        stubCandidates(compatibleWorker, incompatibleFavourite, acceptsPets = false)

        val matches = service.match(command(incompatibleFavourite.id, hasPets = true))

        assertEquals(listOf(compatibleWorker.id), matches.map { it.workerId })
    }

    @Test
    fun `Veteran preference returns only veteran workers`() {
        val standard = profile(WorkerTier.STANDARD, "Standard")
        val veteran = profile(WorkerTier.VETERAN, "Veteran")
        stubCandidates(standard, veteran, acceptsPets = true)

        val matches = service.match(command(UUID.randomUUID(), false).copy(expertPreference = "VETERAN"))

        assertEquals(listOf(veteran.id), matches.map { it.workerId })
    }

    @Test
    fun `unavailable favourite falls back to nearest compatible worker in the same tier`() {
        val favourite = profile(WorkerTier.VETERAN, "Favourite")
        val near = profile(WorkerTier.VETERAN, "Near").apply {
            currentLongitude = BigDecimal("72.878000")
            ratingSum = 30
        }
        val far = profile(WorkerTier.VETERAN, "Far").apply {
            currentLongitude = BigDecimal("72.900000")
            ratingSum = 50
        }
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(favourite, near, far)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(skill(near.id), skill(far.id))
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(near.id), available(far.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(
            compatibility(near.id, true), compatibility(far.id, true),
        )

        val matches = service.match(
            command(favourite.id, false).copy(
                expertPreference = "FAVOURITE",
                favouriteWorkerId = favourite.id,
                fallbackPolicy = "AUTO_ASSIGN",
            ),
        )

        assertEquals(near.id, matches.first().workerId)
        assertEquals("FALLBACK_SAME_TIER", matches.first().assignmentType)
    }

    @Test
    fun `Plus favourite fallback upgrades to nearest veteran only after same tier is exhausted`() {
        val favourite = profile(WorkerTier.STANDARD, "Favourite")
        val veteran = profile(WorkerTier.VETERAN, "Veteran")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(favourite, veteran)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(skill(veteran.id))
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(veteran.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(compatibility(veteran.id, true))

        val matches = service.match(
            command(favourite.id, false).copy(
                expertPreference = "FAVOURITE",
                favouriteWorkerId = favourite.id,
                fallbackPolicy = "AUTO_ASSIGN",
            ),
        )

        assertEquals(listOf(veteran.id), matches.map { it.workerId })
        assertEquals("FALLBACK_PLUS_UPGRADE", matches.single().assignmentType)
    }

    @Test
    fun `skip and refund does not substitute an unavailable favourite`() {
        val favourite = profile(WorkerTier.STANDARD, "Favourite")
        val other = profile(WorkerTier.STANDARD, "Other")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(favourite, other)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(skill(other.id))
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(other.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(compatibility(other.id, true))

        val matches = service.match(
            command(favourite.id, false).copy(
                expertPreference = "FAVOURITE",
                favouriteWorkerId = favourite.id,
                fallbackPolicy = "SKIP_AND_REFUND",
            ),
        )

        assertEquals(0, matches.size)
    }

    @Test
    fun `utensil worker and chemical constraints are hard compatibility filters`() {
        val incompatible = profile(WorkerTier.STANDARD, "Cannot comply")
        val compatible = profile(WorkerTier.STANDARD, "Can comply")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(incompatible, compatible)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(
            skill(incompatible.id), skill(compatible.id),
        )
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(incompatible.id), available(compatible.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(
            compatibility(incompatible.id, true),
            compatibility(
                compatible.id,
                true,
                utensilPreferences = listOf("SEPARATE_UTENSILS"),
                workerConstraints = listOf("MASK_REQUIRED"),
                avoidedChemicals = listOf("BLEACH"),
            ),
        )

        val matches = service.match(
            command(UUID.randomUUID(), hasPets = false).copy(
                utensilPreferences = listOf("separate_utensils"),
                workerConstraints = listOf("mask_required"),
                disallowedChemicals = listOf("bleach"),
            ),
        )

        assertEquals(listOf(compatible.id), matches.map { it.workerId })
    }

    @Test
    fun `allergy and religious food restrictions remove workers who cannot comply`() {
        val incompatible = profile(WorkerTier.STANDARD, "Cannot comply")
        val compatible = profile(WorkerTier.STANDARD, "Can comply")
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(incompatible, compatible)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(
            skill(incompatible.id), skill(compatible.id),
        )
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(incompatible.id), available(compatible.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(
            compatibility(incompatible.id, true),
            compatibility(
                compatible.id,
                true,
                allergyConstraints = listOf("DUST"),
                religiousFoodRestrictions = listOf("NO_PORK"),
            ),
        )

        val matches = service.match(
            command(UUID.randomUUID(), hasPets = false).copy(
                allergies = listOf("dust"),
                religiousFoodRestrictions = listOf("no_pork"),
            ),
        )

        assertEquals(listOf(compatible.id), matches.map { it.workerId })
    }

    private fun stubCandidates(first: WorkerProfile, second: WorkerProfile, acceptsPets: Boolean) {
        every { profiles.findAllByStatus(WorkerStatus.ACTIVE) } returns listOf(first, second)
        every { skills.findAllByIdServiceCodeAndEnabledTrue("HOUSE_CLEANING") } returns listOf(
            skill(first.id),
            skill(second.id),
        )
        every {
            availability.findAllByServiceDateAndStatus(LocalDate.parse("2026-10-03"), AvailabilityStatus.AVAILABLE)
        } returns listOf(available(first.id), available(second.id))
        every { compatibility.findAllByWorkerIdIn(any()) } returns listOf(
            compatibility(first.id, true),
            compatibility(second.id, acceptsPets),
        )
    }

    private fun command(favouriteId: UUID, hasPets: Boolean) = MatchWorkersCommand(
        serviceCode = "HOUSE_CLEANING",
        basePricePerHourMinor = 18_000,
        startsAt = start,
        durationMinutes = 120,
        latitude = 19.076090,
        longitude = 72.877426,
        maxDistanceKm = 15.0,
        hasPets = hasPets,
        foodContext = "VEGETARIAN",
        utensilPreferences = emptyList(),
        preferredLanguages = listOf("Hindi"),
        allergies = emptyList(),
        religiousFoodRestrictions = emptyList(),
        workerConstraints = emptyList(),
        heavyLiftingRequired = false,
        disallowedChemicals = emptyList(),
        requiredEquipment = listOf("MOP"),
        accessibilityNeeds = emptyList(),
        favouriteWorkerIds = listOf(favouriteId),
        plusActive = true,
    )

    private fun profile(tier: WorkerTier, name: String) = WorkerProfile(
        id = UUID.randomUUID(),
        phoneE164 = "+919${UUID.randomUUID().toString().filter(Char::isDigit).padEnd(9, '1').take(9)}",
        displayName = name,
        tier = tier,
        status = WorkerStatus.ACTIVE,
        currentLatitude = BigDecimal("19.076090"),
        currentLongitude = BigDecimal("72.877426"),
        serviceRadiusKm = BigDecimal("15.00"),
        ratingSum = 45,
        ratingCount = 10,
        createdAt = start,
        updatedAt = start,
    )

    private fun skill(workerId: UUID) = WorkerSkill(
        WorkerSkillId(workerId, "HOUSE_CLEANING"),
        basePricePerHourMinor = 18_000,
        enabled = true,
    )

    private fun available(workerId: UUID) = WorkerAvailability(
        id = UUID.randomUUID(),
        workerId = workerId,
        serviceDate = LocalDate.parse("2026-10-03"),
        availableFrom = LocalTime.of(8, 0),
        availableUntil = LocalTime.of(19, 0),
        status = AvailabilityStatus.AVAILABLE,
    )

    private fun compatibility(
        workerId: UUID,
        acceptsPets: Boolean,
        utensilPreferences: List<String> = emptyList(),
        workerConstraints: List<String> = emptyList(),
        avoidedChemicals: List<String> = emptyList(),
        allergyConstraints: List<String> = emptyList(),
        religiousFoodRestrictions: List<String> = emptyList(),
    ) = WorkerCompatibility(
        workerId = workerId,
        acceptsPets = acceptsPets,
        languages = listOf("HINDI"),
        acceptedFoodContexts = listOf("VEGETARIAN"),
        supportedUtensilPreferences = utensilPreferences,
        supportedWorkerConstraints = workerConstraints,
        supportedAllergyConstraints = allergyConstraints,
        acceptedReligiousFoodRestrictions = religiousFoodRestrictions,
        canHeavyLift = true,
        availableEquipment = listOf("MOP"),
        avoidedChemicals = avoidedChemicals,
        accessibilityCapabilities = emptyList(),
        updatedAt = start,
    )
}
