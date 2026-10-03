package com.snabbit.prototype.booking.catalog

import com.snabbit.prototype.booking.domain.ServiceOffering
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceOfferingRepository : JpaRepository<ServiceOffering, UUID> {
    fun findAllByActiveTrueOrderByDisplayName(): List<ServiceOffering>
}
