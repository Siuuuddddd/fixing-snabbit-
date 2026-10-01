package com.snabbit.prototype.user.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
)
