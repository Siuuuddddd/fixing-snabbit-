package com.snabbit.prototype.worker.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

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
)
