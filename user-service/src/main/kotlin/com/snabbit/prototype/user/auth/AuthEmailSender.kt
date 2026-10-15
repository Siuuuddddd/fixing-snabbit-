package com.snabbit.prototype.user.auth

interface AuthEmailSender {
    fun sendVerificationCode(email: String, code: String)
    fun sendResetCode(email: String, code: String)
    fun sendExistingAccountNotice(email: String)
}
