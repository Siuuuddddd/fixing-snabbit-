package com.snabbit.prototype.user.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("phone-auth")
data class PhoneAuthProperties(
    val resendCooldown: Duration = Duration.ofSeconds(30),
    val maxSendsPerHour: Long = 5,
    val maxAttempts: Long = 5,
    val rateLimitWindow: Duration = Duration.ofMinutes(1),
    val rateLimitPerIp: Long = 30,
)
