package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.RecipientContact
import com.snabbit.prototype.notification.events.NotificationIntegrationProperties
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class NotificationContactClient(factory: InternalServiceClientFactory, properties: NotificationIntegrationProperties) {
    private val userClient = factory.create(properties.userServiceUrl)
    private val workerClient = factory.create(properties.workerServiceUrl)

    fun contact(recipientType: RecipientType, recipientId: UUID): RecipientContact = when (recipientType) {
        RecipientType.CUSTOMER -> userClient.get().uri("/internal/v1/users/{userId}/contact", recipientId)
        RecipientType.WORKER -> workerClient.get().uri("/internal/v1/workers/{workerId}/contact", recipientId)
    }
        .retrieve()
        .body(RecipientContact::class.java)
        ?: error("Recipient service returned an empty contact")
}
