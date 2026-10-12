package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.enums.AssignmentType
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy
import com.snabbit.prototype.booking.dto.request.WorkerMatchRequest
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.dto.response.VisitRecoveryContext
import com.snabbit.prototype.booking.dto.response.WorkerMatchResponse
import com.snabbit.prototype.booking.matching.MatchingClients
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssignmentRecoveryServiceTest {
    private val lifecycle = mockk<BookingLifecycleService>()
    private val catalog = mockk<ServiceCatalogService>()
    private val clients = mockk<MatchingClients>()
    private val service = AssignmentRecoveryService(lifecycle, catalog, clients)

    @Test
    fun `auto assign replaces a dropout with the nearest same-tier worker`() = runBlocking {
        val context = context(FallbackPolicy.AUTO_ASSIGN)
        val replacement = UUID.randomUUID()
        val request = slot<WorkerMatchRequest>()
        every { lifecycle.recoveryContext(context.bookingId, context.visitIndex, context.failedWorkerId) } returns context
        every { catalog.requireActive(context.serviceId) } returns offering(context.serviceId)
        coEvery { clients.matches(capture(request)) } returns listOf(match(replacement, "EXPERT"))
        every {
            lifecycle.reassignVisit(
                context.bookingId,
                context.visitIndex,
                context.failedWorkerId,
                replacement,
                "EXPERT",
                AssignmentType.FALLBACK_SAME_TIER,
            )
        } returns snapshot(context.customerId)

        service.recover(context.bookingId, context.visitIndex, context.failedWorkerId)

        assertEquals("EXPERT", request.captured.requiredTier)
        assertEquals("ANY", request.captured.expertPreference)
        verify(exactly = 0) { lifecycle.refundVisitForDropout(any(), any(), any()) }
    }

    @Test
    fun `skip and refund bypasses replacement matching`() = runBlocking {
        val context = context(FallbackPolicy.SKIP_AND_REFUND)
        every { lifecycle.recoveryContext(context.bookingId, context.visitIndex, context.failedWorkerId) } returns context
        every {
            lifecycle.refundVisitForDropout(context.bookingId, context.visitIndex, context.failedWorkerId)
        } returns snapshot(context.customerId)

        service.recover(context.bookingId, context.visitIndex, context.failedWorkerId)

        verify(exactly = 0) { catalog.requireActive(any()) }
    }

    @Test
    fun `auto assign refunds only the failed visit when no same-tier replacement exists`() = runBlocking {
        val context = context(FallbackPolicy.AUTO_ASSIGN)
        every { lifecycle.recoveryContext(context.bookingId, context.visitIndex, context.failedWorkerId) } returns context
        every { catalog.requireActive(context.serviceId) } returns offering(context.serviceId)
        coEvery { clients.matches(any()) } returns emptyList()
        every {
            lifecycle.refundVisitForDropout(context.bookingId, context.visitIndex, context.failedWorkerId)
        } returns snapshot(context.customerId)

        service.recover(context.bookingId, context.visitIndex, context.failedWorkerId)

        verify(exactly = 1) {
            lifecycle.refundVisitForDropout(context.bookingId, context.visitIndex, context.failedWorkerId)
        }
    }

    private fun context(policy: FallbackPolicy) = VisitRecoveryContext(
        bookingId = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        visitIndex = 2,
        failedWorkerId = UUID.randomUUID(),
        startsAt = Instant.parse("2026-10-05T04:30:00Z"),
        durationMinutes = 120,
        quotedPriceMinor = 36_000,
        assignedWorkerTier = "EXPERT",
        latitude = BigDecimal("19.076090"),
        longitude = BigDecimal("72.877426"),
        requirements = BookingRequirements(),
        expertPreference = ExpertPreference.FAVOURITE,
        favouriteWorkerId = UUID.randomUUID(),
        fallbackPolicy = policy,
    )

    private fun offering(serviceId: UUID) = ServiceOfferingSnapshot(
        serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
    )

    private fun match(workerId: UUID, tier: String) = WorkerMatchResponse(
        workerId, "Replacement", tier, BigDecimal("4.80"), BigDecimal.ONE, false, 36_000,
    )

    private fun snapshot(customerId: UUID) = BookingSnapshot(
        UUID.randomUUID(), customerId, "CONFIRMED", 36_000, 36_000, 0, null,
        BookingRequirements(), emptyList(),
    )
}
