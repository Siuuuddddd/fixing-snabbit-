package com.snabbit.prototype.booking.catalog

import com.snabbit.prototype.booking.domain.ServiceOffering
import com.snabbit.prototype.booking.support.BookingRuleViolation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ServiceOfferingRepository : JpaRepository<ServiceOffering, UUID> {
    fun findAllByActiveTrueOrderByDisplayName(): List<ServiceOffering>
}
