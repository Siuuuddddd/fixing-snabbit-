package com.snabbit.prototype.user.dto.response

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.HouseholdPreferenceRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

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
