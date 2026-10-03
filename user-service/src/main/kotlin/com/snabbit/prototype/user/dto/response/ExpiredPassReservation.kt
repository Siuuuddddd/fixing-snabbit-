package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class ExpiredPassReservation(val passId: UUID, val bookingId: UUID, val userId: UUID)
