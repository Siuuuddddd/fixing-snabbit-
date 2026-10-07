package com.snabbit.prototype.notification.delivery

interface PushDeliveryAdapter {
    fun send(tokens: List<String>, message: PushMessage): PushDeliveryResult
}
