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

data class ServiceOfferingSnapshot(
    val serviceId: UUID,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)

@Service
class ServiceCatalogService(private val offerings: ServiceOfferingRepository) {
    @Transactional(readOnly = true)
    fun list(): List<ServiceOfferingSnapshot> =
        offerings.findAllByActiveTrueOrderByDisplayName().map(::snapshot)

    @Transactional(readOnly = true)
    fun requireActive(serviceId: UUID): ServiceOfferingSnapshot {
        val offering = offerings.findById(serviceId).orElseThrow {
            BookingRuleViolation("Service is not available")
        }
        if (!offering.active) throw BookingRuleViolation("Service is not available")
        return snapshot(offering)
    }

    private fun snapshot(offering: ServiceOffering) = ServiceOfferingSnapshot(
        offering.id,
        offering.code,
        offering.displayName,
        offering.basePricePerHourMinor,
    )
}
