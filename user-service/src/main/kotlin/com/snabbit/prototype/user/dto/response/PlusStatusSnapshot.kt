package com.snabbit.prototype.user.dto.response

import com.snabbit.prototype.user.domain.enums.PlusPassStatus

import com.snabbit.prototype.user.service.UserNotFound
import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusSubscription
import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.repository.PlusSubscriptionRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import com.snabbit.prototype.user.events.UserOutboxWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.min

data class PlusStatusSnapshot(
    val userId: UUID,
    val active: Boolean,
    val expiresAt: Instant?,
    val availablePasses: Long,
)
