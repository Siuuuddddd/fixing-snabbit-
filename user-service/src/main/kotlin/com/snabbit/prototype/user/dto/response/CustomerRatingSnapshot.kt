package com.snabbit.prototype.user.dto.response

import com.snabbit.prototype.user.domain.CustomerRating
import com.snabbit.prototype.user.repository.CustomerRatingRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import com.snabbit.prototype.user.events.UserOutboxWriter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class CustomerRatingSnapshot(
    val ratingId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val customerId: UUID,
    val workerId: UUID,
    val stars: Int,
    val comment: String?,
    val customerAverageRating: BigDecimal,
)
