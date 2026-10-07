package com.snabbit.prototype.notification.delivery

interface FcmClient {
    fun send(tokens: List<String>, message: PushMessage): PushDeliveryResult
}
