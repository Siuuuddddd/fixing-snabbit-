package com.snabbit.prototype.user.service

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

class UserNotFound(id: UUID) : RuntimeException("User $id was not found")
