package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationDeliveryQuery(private val devices: DeviceRegistrationRepository) {
    @Transactional(readOnly = true)
    fun pushTokens(recipientType: RecipientType, recipientId: UUID): List<String> =
        devices.findAllByRecipientTypeAndRecipientIdAndActiveTrue(recipientType, recipientId).map { it.pushToken }
}
