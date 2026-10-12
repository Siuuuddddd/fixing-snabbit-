package com.snabbit.prototype.booking.exception

import java.time.Instant

data class VisitValidationError(val visitIndex: Int, val startsAt: Instant, val message: String)

class PlanValidationException(val visitErrors: List<VisitValidationError>) : RuntimeException(
    "One or more requested visits are invalid",
)
