package com.snabbit.prototype.booking.exception

import java.time.Instant

data class VisitHoldFailure(
    val visitIndex: Int,
    val requestedStart: Instant,
    val reason: String,
    val alternatives: List<Instant>,
)

class AtomicHoldUnavailable(val failures: List<VisitHoldFailure>) : RuntimeException(
    "Unable to hold all requested visits",
)
