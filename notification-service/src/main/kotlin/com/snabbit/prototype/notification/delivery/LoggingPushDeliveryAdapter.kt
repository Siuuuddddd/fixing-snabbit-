package com.snabbit.prototype.notification.delivery

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
@Profile("local", "test")
class LoggingPushDeliveryAdapter : PushDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(tokens: List<String>, title: String, body: String) {
        if (tokens.isEmpty()) error("Recipient has no active push device")
        logger.info("Development push delivered to {} device(s): {}", tokens.size, title)
    }
}
