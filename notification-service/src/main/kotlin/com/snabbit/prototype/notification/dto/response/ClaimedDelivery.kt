package com.snabbit.prototype.notification.dto.response

import com.snabbit.prototype.notification.domain.enums.RecipientType
import java.util.UUID

data class ClaimedDelivery(
    val deliveryId: UUID,
    val notificationId: Long,
    val recipientType: RecipientType,
    val recipientId: UUID,
    val channel: String,
    val title: String,
    val body: String,
    val attempts: Int,
)
