package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.SmsGateway
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

interface OtpDelivery {
    fun deliver(phoneE164: String, code: String)
}

@Component
@Profile("local", "test")
class LoggingOtpDelivery : OtpDelivery {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun deliver(phoneE164: String, code: String) {
        logger.info("Prototype OTP generated for {}: {}", phoneE164.takeLast(4).padStart(phoneE164.length, '*'), code)
    }
}

@Component
@Profile("!local & !test")
class SmsOtpDelivery(private val sms: SmsGateway) : OtpDelivery {
    override fun deliver(phoneE164: String, code: String) {
        sms.send(phoneE164, "Your Snabbit verification code is $code. It expires in 5 minutes.")
    }
}
