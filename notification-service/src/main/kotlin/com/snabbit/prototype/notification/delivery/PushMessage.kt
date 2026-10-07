package com.snabbit.prototype.notification.delivery

data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val channelId: String,
)

data class PushDeliveryResult(val invalidTokens: Set<String> = emptySet())
