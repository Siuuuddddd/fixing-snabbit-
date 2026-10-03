package com.snabbit.prototype.user.auth

interface OtpDelivery {
    fun deliver(phoneE164: String, code: String)
}
