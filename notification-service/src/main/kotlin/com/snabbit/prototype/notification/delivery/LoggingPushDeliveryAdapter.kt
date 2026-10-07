package com.snabbit.prototype.notification.delivery

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "notifications.push",
    name = ["provider"],
    havingValue = "logging",
    matchIfMissing = true,
)
class LoggingPushDeliveryAdapter : PushDeliveryAdapter {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun send(tokens: List<String>, message: PushMessage): PushDeliveryResult {
        if (tokens.isEmpty()) error("Recipient has no active push device")
        logger.info("Development push delivered to {} device(s): {}", tokens.size, message.title)
        return PushDeliveryResult()
    }
}
