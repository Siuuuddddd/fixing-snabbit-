package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.enums.WorkerCompatibilityReason
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import org.springframework.stereotype.Component

@Component
class WorkerCompatibilityPolicy {
    fun evaluate(
        requirements: MatchWorkersCommand,
        worker: WorkerCompatibility?,
    ): WorkerCompatibilityResult {
        if (worker == null) {
            return WorkerCompatibilityResult(
                compatible = false,
                reasons = setOf(WorkerCompatibilityReason.MISSING_WORKER_PREFERENCES),
            )
        }

        val reasons = linkedSetOf<WorkerCompatibilityReason>()
        val languages = worker.languages.normalized()
        val equipment = worker.availableEquipment.normalized()
        val accessibility = worker.accessibilityCapabilities.normalized()
        val utensilPreferences = worker.supportedUtensilPreferences.normalized()
        val professionalRequirements = worker.supportedWorkerConstraints.normalized()
        val avoidedChemicals = worker.avoidedChemicals.normalized()
        val supportedAllergies = worker.supportedAllergyConstraints.normalized()
        val acceptedReligiousFoodRestrictions = worker.acceptedReligiousFoodRestrictions.normalized()

        if (requirements.hasPets && !worker.acceptsPets) {
            reasons += WorkerCompatibilityReason.PETS_NOT_ACCEPTED
        }
        requirements.foodContext?.normalizedValue()?.takeIf(String::isNotBlank)?.let { requested ->
            if (requested !in worker.acceptedFoodContexts.normalized()) {
                reasons += WorkerCompatibilityReason.FOOD_CONTEXT_NOT_ACCEPTED
            }
        }
        val requestedLanguages = requirements.preferredLanguages.normalized()
        if (requestedLanguages.isNotEmpty() && requestedLanguages.none(languages::contains)) {
            reasons += WorkerCompatibilityReason.LANGUAGE_NOT_SUPPORTED
        }
        if (!utensilPreferences.containsAll(requirements.utensilPreferences.normalized())) {
            reasons += WorkerCompatibilityReason.UTENSIL_PREFERENCE_NOT_SUPPORTED
        }
        if (!supportedAllergies.containsAll(requirements.allergies.normalized())) {
            reasons += WorkerCompatibilityReason.ALLERGY_NOT_SUPPORTED
        }
        if (!acceptedReligiousFoodRestrictions.containsAll(requirements.religiousFoodRestrictions.normalized())) {
            reasons += WorkerCompatibilityReason.RELIGIOUS_FOOD_RESTRICTION_NOT_SUPPORTED
        }
        if (!professionalRequirements.containsAll(requirements.workerConstraints.normalized())) {
            reasons += WorkerCompatibilityReason.PROFESSIONAL_REQUIREMENT_NOT_MET
        }
        if (requirements.heavyLiftingRequired && !worker.canHeavyLift) {
            reasons += WorkerCompatibilityReason.HEAVY_LIFTING_NOT_SUPPORTED
        }
        if (!avoidedChemicals.containsAll(requirements.disallowedChemicals.normalized())) {
            reasons += WorkerCompatibilityReason.CHEMICAL_RESTRICTION_NOT_SUPPORTED
        }
        if (!equipment.containsAll(requirements.requiredEquipment.normalized())) {
            reasons += WorkerCompatibilityReason.EQUIPMENT_NOT_AVAILABLE
        }
        if (!accessibility.containsAll(requirements.accessibilityNeeds.normalized())) {
            reasons += WorkerCompatibilityReason.ACCESSIBILITY_NEED_NOT_SUPPORTED
        }

        return WorkerCompatibilityResult(reasons.isEmpty(), reasons)
    }

    private fun Iterable<String>.normalized(): Set<String> = map { it.normalizedValue() }.filter(String::isNotBlank).toSet()

    private fun String.normalizedValue(): String = trim().uppercase()
}
