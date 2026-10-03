package com.snabbit.prototype.user.service

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.dto.request.HouseholdCommand
import com.snabbit.prototype.user.dto.response.HouseholdSnapshot
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.FavouriteLimitReached
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.HouseholdPreferenceRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HouseholdService(
    private val userRepository: UserProfileRepository,
    private val preferenceRepository: HouseholdPreferenceRepository,
    private val favouriteRepository: FavouriteWorkerRepository,
    private val clock: Clock,
    private val outbox: UserOutboxWriter,
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
        outbox.append(
            userId,
            "HouseholdPreferencesUpdated",
            mapOf("userId" to userId, "updatedAt" to preference.updatedAt),
            preference.updatedAt,
        )
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
        outbox.append(
            userId,
            "FavouriteWorkerAdded",
            mapOf("userId" to userId, "workerId" to workerId, "priority" to priority),
            clock.instant(),
        )
        val preference = preferenceRepository.findById(userId).orElse(defaultPreference(userId))
        return snapshot(user.id, user.hasActivePlus(clock.instant()), preference)
    }

    @Transactional
    fun removeFavourite(userId: UUID, workerId: UUID): HouseholdSnapshot {
        val user = userRepository.findForUpdate(userId) ?: throw UserNotFound(userId)
        favouriteRepository.deleteById(FavouriteWorkerId(userId, workerId))
        outbox.append(
            userId,
            "FavouriteWorkerRemoved",
            mapOf("userId" to userId, "workerId" to workerId),
            clock.instant(),
        )
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
