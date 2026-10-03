package com.snabbit.prototype.worker.dto.response

import com.snabbit.prototype.worker.dto.response.BookingRatingVisit

import com.snabbit.prototype.worker.domain.WorkerRating
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRatingRepository
import com.snabbit.prototype.worker.security.InternalServiceClientFactory
import com.snabbit.prototype.worker.events.WorkerOutboxWriter
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class BookingRatingContext(
    val bookingId: UUID,
    val customerId: UUID,
    val visits: List<BookingRatingVisit>,
)
