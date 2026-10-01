package com.snabbit.prototype.user.application

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.persistence.FavouriteWorkerRepository
import com.snabbit.prototype.user.persistence.HouseholdPreferenceRepository
import com.snabbit.prototype.user.persistence.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class HouseholdCommand(
    val hasPets: Boolean,
    val petDetails: String?,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val allergies: List<String>,
    val religiousFoodRestrictions: List<String>,
    val accessibilityNeeds: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
)

data class HouseholdSnapshot(
    val userId: UUID,
    val plusActive: Boolean,
    val favouriteWorkerIds: List<UUID>,
    val hasPets: Boolean,
    val petDetails: String?,
    val foodContext: String?,
    val utensilPreferences: List<String>,
    val preferredLanguages: List<String>,
    val allergies: List<String>,
    val religiousFoodRestrictions: List<String>,
    val accessibilityNeeds: List<String>,
    val workerConstraints: List<String>,
    val heavyLiftingRequired: Boolean,
    val disallowedChemicals: List<String>,
    val requiredEquipment: List<String>,
)

class UserNotFound(id: UUID) : RuntimeException("User $id was not found")
class FavouriteLimitReached : RuntimeException("A customer can have at most three favourite workers")

@Service
class HouseholdService(
    private val userRepository: UserProfileRepository,
    private val preferenceRepository: HouseholdPreferenceRepository,
    private val favouriteRepository: FavouriteWorkerRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun snapshot(userId: UUID): HouseholdSnapshot {
        val user = userRepository.findById(userId).orElseThrow { UserNotFound(userId) }
        val preference = preferenceRepository.findById(userId).orElse(defaultPreference(userId))
        return snapshot(user.id, user.hasActivePlus(clock.instant()), preference)
    }

    @Transactional
    fun update(userId: UUID, command: HouseholdCommand): HouseholdSnapshot {
        val user = userRepository.findForUpdate(userId) ?: throw UserNotFound(userId)
        val preference = preferenceRepository.findById(userId).orElse(defaultPreference(userId)).apply {
            hasPets = command.hasPets
            petDetails = command.petDetails?.trim()?.takeIf(String::isNotBlank)
            foodContext = command.foodContext?.trim()?.uppercase()?.takeIf(String::isNotBlank)
            utensilPreferences = normalized(command.utensilPreferences)
            preferredLanguages = normalized(command.preferredLanguages)
            allergies = normalized(command.allergies)
            religiousFoodRestrictions = normalized(command.religiousFoodRestrictions)
            accessibilityNeeds = normalized(command.accessibilityNeeds)
            workerConstraints = normalized(command.workerConstraints)
            heavyLiftingRequired = command.heavyLiftingRequired
            disallowedChemicals = normalized(command.disallowedChemicals)
            requiredEquipment = normalized(command.requiredEquipment)
            updatedAt = clock.instant()
        }
        preferenceRepository.save(preference)
        return snapshot(user.id, user.hasActivePlus(clock.instant()), preference)
    }

    @Transactional
    fun addFavourite(userId: UUID, workerId: UUID): HouseholdSnapshot {
        val user = userRepository.findForUpdate(userId) ?: throw UserNotFound(userId)
        val current = favouriteRepository.findAllByIdUserIdOrderByPriority(userId)
        if (current.any { it.id.workerId == workerId }) return loadSnapshot(userId)
        if (current.size >= 3) throw FavouriteLimitReached()
        val usedPriorities = current.map(FavouriteWorker::priority).toSet()
        val priority = (1..3).first { it !in usedPriorities }
        favouriteRepository.save(
            FavouriteWorker(FavouriteWorkerId(userId, workerId), priority, clock.instant()),
        )
        val preference = preferenceRepository.findById(userId).orElse(defaultPreference(userId))
        return snapshot(user.id, user.hasActivePlus(clock.instant()), preference)
    }

    @Transactional
    fun removeFavourite(userId: UUID, workerId: UUID): HouseholdSnapshot {
        val user = userRepository.findForUpdate(userId) ?: throw UserNotFound(userId)
        favouriteRepository.deleteById(FavouriteWorkerId(userId, workerId))
        val preference = preferenceRepository.findById(userId).orElse(defaultPreference(userId))
        return snapshot(user.id, user.hasActivePlus(clock.instant()), preference)
    }

    private fun snapshot(userId: UUID, plusActive: Boolean, preference: HouseholdPreference): HouseholdSnapshot =
        HouseholdSnapshot(
            userId = userId,
            plusActive = plusActive,
            favouriteWorkerIds = favouriteRepository.findAllByIdUserIdOrderByPriority(userId).map { it.id.workerId },
            hasPets = preference.hasPets,
            petDetails = preference.petDetails,
            foodContext = preference.foodContext,
            utensilPreferences = preference.utensilPreferences,
            preferredLanguages = preference.preferredLanguages,
            allergies = preference.allergies,
            religiousFoodRestrictions = preference.religiousFoodRestrictions,
            accessibilityNeeds = preference.accessibilityNeeds,
            workerConstraints = preference.workerConstraints,
            heavyLiftingRequired = preference.heavyLiftingRequired,
            disallowedChemicals = preference.disallowedChemicals,
            requiredEquipment = preference.requiredEquipment,
        )

    private fun loadSnapshot(userId: UUID): HouseholdSnapshot {
        val user = userRepository.findById(userId).orElseThrow { UserNotFound(userId) }
        return snapshot(
            userId,
            user.hasActivePlus(clock.instant()),
            preferenceRepository.findById(userId).orElse(defaultPreference(userId)),
        )
    }

    private fun defaultPreference(userId: UUID) = HouseholdPreference(
        userId,
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
        clock.instant(),
    )

    private fun normalized(values: List<String>): List<String> =
        values.map(String::trim).filter(String::isNotBlank).map(String::uppercase).distinct().sorted()
}
