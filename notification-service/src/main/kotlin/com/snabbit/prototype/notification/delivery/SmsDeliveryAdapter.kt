package com.snabbit.prototype.notification.delivery

interface SmsDeliveryAdapter {
    fun send(phoneNumber: String, body: String)
}
