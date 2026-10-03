package com.snabbit.prototype.user.auth

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "test")
class LoggingOtpDelivery : OtpDelivery {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun deliver(phoneE164: String, code: String) {
        logger.info("Prototype OTP generated for {}: {}", phoneE164.takeLast(4).padStart(phoneE164.length, '*'), code)
    }
}
