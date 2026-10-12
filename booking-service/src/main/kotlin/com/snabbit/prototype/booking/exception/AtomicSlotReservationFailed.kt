package com.snabbit.prototype.booking.exception

import java.time.Instant

data class ConcurrentVisitFailure(
    val visitIndex: Int,
    val startsAt: Instant,
    val detail: String,
)

class AtomicSlotReservationFailed(
    val failures: List<ConcurrentVisitFailure>,
) : SlotUnavailable("One or more requested visits became unavailable")
