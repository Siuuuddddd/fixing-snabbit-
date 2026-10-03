package com.snabbit.prototype.notification.delivery

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!local & !test")
class FcmPushDeliveryAdapter(private val client: FcmClient) : PushDeliveryAdapter {
    override fun send(tokens: List<String>, title: String, body: String) = client.send(tokens, title, body)
}
