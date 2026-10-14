package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.WorkerSelectionSource
import java.util.UUID

data class ApplyWorkerSelectionCommand(
    val bookingId: UUID,
    val customerId: UUID,
    val workerId: UUID,
    val tier: String,
    val tierSurchargeMinor: Long,
    val grossVisitPricesMinor: Map<Int, Long>,
    val source: WorkerSelectionSource,
)
