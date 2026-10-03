package com.snabbit.prototype.notification.delivery

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "test")
class LoggingSmsDeliveryAdapter : SmsDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(phoneNumber: String, body: String) {
        logger.info("Development SMS delivered to ******{}", phoneNumber.takeLast(4))
    }
}
