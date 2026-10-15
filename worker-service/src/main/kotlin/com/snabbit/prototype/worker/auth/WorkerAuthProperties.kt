package com.snabbit.prototype.worker.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth")
data class WorkerAuthProperties(
    val issuer: String,
    val jwtSecret: String,
    val accessTokenTtl: Duration,
    val sharedSelectionTokenTtl: Duration = Duration.ofMinutes(5),
    val refreshIdleTtl: Duration = Duration.ofDays(180),
    val refreshAbsoluteTtl: Duration? = null,
    val refreshGraceWindow: Duration = Duration.ofSeconds(10),
    val maxActiveSessions: Int = 10,
    val accountRateLimit: Long = 20,
    val accountRateWindow: Duration = Duration.ofMinutes(1),
)
