package com.snabbit.prototype.booking.catalog

import com.snabbit.prototype.booking.domain.ServiceOffering
import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
