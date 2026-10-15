package com.snabbit.prototype.user.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth")
data class AuthProperties(
    val issuer: String,
    val jwtSecret: String,
    val otpPepper: String,
    val otpTtl: Duration,
    val requestCooldown: Duration,
    val accessTokenTtl: Duration,
    val maxAttempts: Int,
    val exposeCode: Boolean,
    val refreshIdleTtl: Duration = Duration.ofDays(180),
    val refreshAbsoluteTtl: Duration? = null,
    val refreshGraceWindow: Duration = Duration.ofSeconds(10),
    val maxActiveSessions: Int = 10,
    val accountRateLimit: Long = 20,
    val accountRateWindow: Duration = Duration.ofMinutes(1),
)
