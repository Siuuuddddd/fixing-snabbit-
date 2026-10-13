package com.snabbit.prototype.notification.dto.response

import java.util.UUID

data class ArrivalNotificationContext(
    val bookingId: UUID,
    val customerId: UUID,
    val homeContactIsBooker: Boolean,
    val maskedHomeContactPhone: String,
)
