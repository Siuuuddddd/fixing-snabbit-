package com.snabbit.prototype.booking.catalog

import com.snabbit.prototype.booking.domain.ServiceOffering
import com.snabbit.prototype.booking.support.BookingRuleViolation
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class ServiceCatalogServiceTest {
    private val repository = mockk<ServiceOfferingRepository>()
    private val service = ServiceCatalogService(repository)

    @Test
    fun `returns server-owned code and base price for active service`() {
        val offering = ServiceOffering(UUID.randomUUID(), "HOUSE_CLEANING", "House Cleaning", 18_000, true)
        every { repository.findById(offering.id) } returns Optional.of(offering)

        val result = service.requireActive(offering.id)

        assertEquals("HOUSE_CLEANING", result.code)
        assertEquals(18_000, result.basePricePerHourMinor)
    }

    @Test
    fun `inactive service cannot enter matching`() {
        val offering = ServiceOffering(UUID.randomUUID(), "OLD_SERVICE", "Old", 10_000, false)
        every { repository.findById(offering.id) } returns Optional.of(offering)

        assertThrows(BookingRuleViolation::class.java) { service.requireActive(offering.id) }
    }
}
