package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.ServiceOffering
import com.snabbit.prototype.booking.support.BookingRuleViolation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class ServiceOfferingSnapshot(
    val serviceId: UUID,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)
