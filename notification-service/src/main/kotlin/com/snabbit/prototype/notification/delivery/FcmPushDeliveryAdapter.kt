package com.snabbit.prototype.notification.delivery

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "notifications.push", name = ["provider"], havingValue = "fcm")
class FcmPushDeliveryAdapter(private val client: FcmClient) : PushDeliveryAdapter {
    override fun send(tokens: List<String>, message: PushMessage) = client.send(tokens, message)
}
