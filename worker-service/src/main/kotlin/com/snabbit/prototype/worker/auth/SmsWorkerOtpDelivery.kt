package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.SmsGateway
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!local & !test")
class SmsWorkerOtpDelivery(private val sms: SmsGateway) : WorkerOtpDelivery {
    override fun deliver(phoneE164: String, code: String) {
        sms.send(phoneE164, "Your Snabbit worker verification code is $code. It expires in 5 minutes.")
    }
}
