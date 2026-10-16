package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.dto.request.ApplyVisitWorkerSelectionCommand
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class WorkerSelectionService(
    private val options: WorkerOptionsService,
    private val lifecycle: BookingLifecycleService,
) {
    suspend fun select(bookingId: UUID, customerId: UUID, visitIndex: Int, requestedWorkerId: UUID): BookingSnapshot {
        val available = options.options(bookingId, customerId, visitIndex)
        val selected = (available.favourites + available.others)
            .firstOrNull { it.workerId == requestedWorkerId }
            ?: throw BookingRuleViolation("The requested worker is not an eligible booking option")
        if (!selected.selectable || selected.visits.any { !it.available }) {
            throw BookingRuleViolation("The requested worker is no longer available for this visit")
        }
        val visit = selected.visits.single { it.visitIndex == visitIndex }
        return lifecycle.applyVisitWorkerSelection(
            ApplyVisitWorkerSelectionCommand(
                bookingId = bookingId,
                customerId = customerId,
                visitIndex = visitIndex,
                workerId = selected.workerId,
                tier = selected.tier,
                tierSurchargeMinor = selected.tierSurchargeMinor,
                grossVisitPriceMinor = visit.priceMinor,
                workerName = selected.firstName,
                workerRating = selected.averageRating,
                workerRatingCount = selected.ratingCount,
            ),
        )
    }
}
