package com.snabbit.prototype.user.auth

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.snabbit.prototype.platform.OpaqueToken
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender

class EmailAuthLogPrivacyTest {
    @Test
    fun `email sender emits no identity or code into logs`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start(); root.addAppender(it) }
        val email = "privacy@example.test"
        val code = "123456"
        try {
            val sender = SmtpAuthEmailSender(
                mockk<JavaMailSender>(relaxed = true),
                EmailAuthProperties(codeHmacSecret = OpaqueToken.generate()),
            )
            sender.sendVerificationCode(email, code)
            sender.sendResetCode(email, code)
            sender.sendExistingAccountNotice(email)
            val output = appender.list.joinToString("\n") { it.formattedMessage }
            assertFalse(output.contains(email))
            assertFalse(output.contains(code))
        } finally {
            root.detachAppender(appender)
            appender.stop()
        }
    }
}
