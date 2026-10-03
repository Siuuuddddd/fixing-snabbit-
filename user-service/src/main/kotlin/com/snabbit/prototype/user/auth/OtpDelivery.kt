package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.SmsGateway
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

interface OtpDelivery {
    fun deliver(phoneE164: String, code: String)
}
