package com.snabbit.prototype.worker.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth")
data class WorkerAuthProperties(
    val issuer: String,
    val jwtSecret: String,
    val otpPepper: String,
    val otpTtl: Duration,
    val requestCooldown: Duration,
    val accessTokenTtl: Duration,
    val maxAttempts: Int,
    val exposeCode: Boolean,
    val sharedSelectionTokenTtl: Duration = Duration.ofMinutes(5),
    val refreshIdleTtl: Duration = Duration.ofDays(180),
    val refreshAbsoluteTtl: Duration? = null,
    val refreshGraceWindow: Duration = Duration.ofSeconds(10),
    val maxActiveSessions: Int = 10,
    val accountRateLimit: Long = 20,
    val accountRateWindow: Duration = Duration.ofMinutes(1),
)
