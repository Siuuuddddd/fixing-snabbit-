package com.snabbit.prototype.user.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("email-auth")
data class EmailAuthProperties(
    val sender: String = "smtp",
    val from: String = "no-reply@localhost",
    val codeHmacSecret: String,
    val codeTtl: Duration = Duration.ofMinutes(10),
    val codeMaxAttempts: Int = 5,
    val resendCooldown: Duration = Duration.ofSeconds(60),
    val maxSendsPerHour: Int = 5,
    val termsVersion: String = "current",
    val passwordMinLength: Int = 8,
    val passwordMaxLength: Int = 128,
    val argon2SaltLength: Int = 16,
    val argon2HashLength: Int = 32,
    val argon2Parallelism: Int = 1,
    val argon2MemoryKb: Int = 19_456,
    val argon2Iterations: Int = 2,
    val bcryptStrength: Int = 12,
    val rateLimitWindow: Duration = Duration.ofMinutes(1),
    val rateLimitPerIdentity: Long = 10,
    val rateLimitPerIp: Long = 30,
    val maximumDelay: Duration = Duration.ofMinutes(5),
)
