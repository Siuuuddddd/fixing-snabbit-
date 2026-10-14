package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.enums.WorkerSelectionSource
import com.snabbit.prototype.booking.dto.request.ApplyWorkerSelectionCommand
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkerSelectionService(
    private val options: WorkerOptionsService,
    private val lifecycle: BookingLifecycleService,
) {
    suspend fun select(bookingId: UUID, customerId: UUID, requestedWorkerId: UUID?): BookingSnapshot {
        val available = options.options(bookingId, customerId)
        val listed = available.favourites + available.veterans
        val selected = if (requestedWorkerId == null) {
            available.favourites.firstOrNull { it.selectable } ?: available.veterans.firstOrNull { it.selectable }
        } else {
            listed.firstOrNull { it.workerId == requestedWorkerId }
        } ?: throw BookingRuleViolation("The requested worker is not an eligible booking option")
        if (!selected.selectable || selected.visits.any { !it.available }) {
            throw BookingRuleViolation("The requested worker is no longer available for every visit")
        }
        return lifecycle.applyWorkerSelection(
            ApplyWorkerSelectionCommand(
                bookingId = bookingId,
                customerId = customerId,
                workerId = selected.workerId,
                tier = selected.tier,
                tierSurchargeMinor = selected.tierSurchargeMinor,
                grossVisitPricesMinor = selected.visits.associate { it.visitIndex to it.priceMinor },
                source = if (requestedWorkerId == null) WorkerSelectionSource.AUTO else WorkerSelectionSource.CUSTOMER,
            ),
        )
    }
}
