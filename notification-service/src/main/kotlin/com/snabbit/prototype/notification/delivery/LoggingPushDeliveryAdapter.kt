package com.snabbit.prototype.notification.delivery

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "test")
class LoggingPushDeliveryAdapter : PushDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(tokens: List<String>, title: String, body: String) {
        if (tokens.isEmpty()) error("Recipient has no active push device")
        logger.info("Development push delivered to {} device(s): {}", tokens.size, title)
    }
}
