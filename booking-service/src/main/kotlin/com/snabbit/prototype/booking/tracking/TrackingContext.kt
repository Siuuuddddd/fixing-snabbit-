package com.snabbit.prototype.booking.tracking

import com.snabbit.prototype.booking.dto.response.VisitSnapshot
import java.util.UUID

internal data class TrackingContext(val bookingId: UUID, val visit: VisitSnapshot)
