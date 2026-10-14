package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.enums.WorkerCompatibilityReason
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerCompatibilityPolicyTest {
    private val policy = WorkerCompatibilityPolicy()

    @Test
    fun `full worker preferences satisfy every household category`() {
        val result = policy.evaluate(fullRequirements(), fullWorker())

        assertTrue(result.compatible)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `each unsupported category reports its exact reason`() {
        val cases = listOf(
            fullWorker(acceptsPets = false) to WorkerCompatibilityReason.PETS_NOT_ACCEPTED,
            fullWorker(food = emptyList()) to WorkerCompatibilityReason.FOOD_CONTEXT_NOT_ACCEPTED,
            fullWorker(languages = emptyList()) to WorkerCompatibilityReason.LANGUAGE_NOT_SUPPORTED,
            fullWorker(utensils = emptyList()) to WorkerCompatibilityReason.UTENSIL_PREFERENCE_NOT_SUPPORTED,
            fullWorker(allergies = emptyList()) to WorkerCompatibilityReason.ALLERGY_NOT_SUPPORTED,
            fullWorker(religiousFood = emptyList()) to WorkerCompatibilityReason.RELIGIOUS_FOOD_RESTRICTION_NOT_SUPPORTED,
            fullWorker(professional = emptyList()) to WorkerCompatibilityReason.PROFESSIONAL_REQUIREMENT_NOT_MET,
            fullWorker(canHeavyLift = false) to WorkerCompatibilityReason.HEAVY_LIFTING_NOT_SUPPORTED,
            fullWorker(chemicals = emptyList()) to WorkerCompatibilityReason.CHEMICAL_RESTRICTION_NOT_SUPPORTED,
            fullWorker(equipment = emptyList()) to WorkerCompatibilityReason.EQUIPMENT_NOT_AVAILABLE,
            fullWorker(accessibility = emptyList()) to WorkerCompatibilityReason.ACCESSIBILITY_NEED_NOT_SUPPORTED,
        )

        cases.forEach { (worker, expected) ->
            val result = policy.evaluate(fullRequirements(), worker)
            assertFalse(result.compatible, expected.name)
            assertEquals(setOf(expected), result.reasons, expected.name)
        }
    }

    @Test
    fun `missing worker preferences are incompatible`() {
        val result = policy.evaluate(fullRequirements(), null)

        assertFalse(result.compatible)
        assertEquals(setOf(WorkerCompatibilityReason.MISSING_WORKER_PREFERENCES), result.reasons)
    }

    @Test
    fun `preferred languages use any overlap while cumulative categories require all values`() {
        val requirements = fullRequirements().copy(preferredLanguages = listOf("English", "Marathi"))
        val oneLanguageWorker = fullWorker(languages = listOf("marathi"))

        assertTrue(policy.evaluate(requirements, oneLanguageWorker).compatible)
    }

    private fun fullRequirements() = MatchWorkersCommand(
        serviceCode = "HOUSE_CLEANING",
        basePricePerHourMinor = 18_000,
        startsAt = Instant.parse("2026-10-15T04:00:00Z"),
        durationMinutes = 120,
        latitude = 19.076090,
        longitude = 72.877426,
        maxDistanceKm = 15.0,
        hasPets = true,
        foodContext = "Jain",
        utensilPreferences = listOf("separate_utensils"),
        preferredLanguages = listOf("Hindi"),
        allergies = listOf("dust", "fragrance"),
        religiousFoodRestrictions = listOf("no_pork"),
        workerConstraints = listOf("mask_required", "shoe_covers"),
        heavyLiftingRequired = true,
        disallowedChemicals = listOf("bleach"),
        requiredEquipment = listOf("mop", "vacuum"),
        accessibilityNeeds = listOf("step_free_access", "hearing_support"),
        favouriteWorkerIds = emptyList(),
        plusActive = false,
    )

    private fun fullWorker(
        acceptsPets: Boolean = true,
        food: List<String> = listOf("VEGETARIAN", "NON_VEGETARIAN", "JAIN"),
        languages: List<String> = listOf("HINDI", "MARATHI", "ENGLISH"),
        utensils: List<String> = listOf("SEPARATE_UTENSILS"),
        allergies: List<String> = listOf("DUST", "FRAGRANCE"),
        religiousFood: List<String> = listOf("NO_PORK"),
        professional: List<String> = listOf("MASK_REQUIRED", "SHOE_COVERS"),
        canHeavyLift: Boolean = true,
        chemicals: List<String> = listOf("BLEACH"),
        equipment: List<String> = listOf("MOP", "VACUUM"),
        accessibility: List<String> = listOf("STEP_FREE_ACCESS", "HEARING_SUPPORT"),
    ) = WorkerCompatibility(
        workerId = UUID.randomUUID(),
        acceptsPets = acceptsPets,
        languages = languages,
        acceptedFoodContexts = food,
        supportedUtensilPreferences = utensils,
        supportedWorkerConstraints = professional,
        supportedAllergyConstraints = allergies,
        acceptedReligiousFoodRestrictions = religiousFood,
        canHeavyLift = canHeavyLift,
        availableEquipment = equipment,
        avoidedChemicals = chemicals,
        accessibilityCapabilities = accessibility,
        updatedAt = Instant.EPOCH,
    )
}
