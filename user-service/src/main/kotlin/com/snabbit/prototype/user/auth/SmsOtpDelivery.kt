package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.SmsGateway
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!local & !test")
class SmsOtpDelivery(private val sms: SmsGateway) : OtpDelivery {
    override fun deliver(phoneE164: String, code: String) {
        sms.send(phoneE164, "Your Snabbit verification code is $code. It expires in 5 minutes.")
    }
}
