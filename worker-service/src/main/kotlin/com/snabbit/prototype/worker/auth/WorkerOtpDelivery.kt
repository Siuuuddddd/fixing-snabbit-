package com.snabbit.prototype.worker.auth

interface WorkerOtpDelivery {
    fun deliver(phoneE164: String, code: String)
}
