package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class BookingRatingVisit(val visitIndex: Int, val workerId: UUID, val completed: Boolean)
