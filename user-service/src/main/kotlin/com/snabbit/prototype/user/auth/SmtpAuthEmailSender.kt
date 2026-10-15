package com.snabbit.prototype.user.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "email-auth", name = ["sender"], havingValue = "smtp", matchIfMissing = true)
class SmtpAuthEmailSender(
    private val mail: JavaMailSender,
    private val properties: EmailAuthProperties,
) : AuthEmailSender {
    override fun sendVerificationCode(email: String, code: String) = send(
        email,
        "Verify your Snabbit email",
        "Your Snabbit verification code is $code. It expires soon.\n\nIgnore this email if this wasn't you.",
    )

    override fun sendResetCode(email: String, code: String) = send(
        email,
        "Reset your Snabbit password",
        "Your Snabbit password reset code is $code. It expires soon.\n\nIgnore this email if this wasn't you.",
    )

    override fun sendExistingAccountNotice(email: String) = send(
        email,
        "Your Snabbit account already exists",
        "An attempt was made to create another Snabbit account with this email. Sign in or reset your password instead.\n\nIgnore this email if this wasn't you.",
    )

    private fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage()
        message.from = properties.from
        message.setTo(to)
        message.subject = subject
        message.text = body
        mail.send(message)
    }
}
