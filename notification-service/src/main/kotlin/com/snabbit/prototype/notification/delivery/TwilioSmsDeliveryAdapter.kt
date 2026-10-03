package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.platform.SmsGateway
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!local & !test")
class TwilioSmsDeliveryAdapter(private val gateway: SmsGateway) : SmsDeliveryAdapter {
    override fun send(phoneNumber: String, body: String) = gateway.send(phoneNumber, body)
}
