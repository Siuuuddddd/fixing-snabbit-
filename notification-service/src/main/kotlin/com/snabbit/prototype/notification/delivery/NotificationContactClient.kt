package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.dto.response.RecipientContact

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.snabbit.prototype.platform.SmsGateway
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.events.NotificationIntegrationProperties
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

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
