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

@Configuration
class WorkerAuthConfiguration {
    @Bean
    fun workerJwtEncoder(properties: WorkerAuthProperties): JwtEncoder {
        require(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "JWT secret must contain at least 32 bytes"
        }
        val key = SecretKeySpec(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
    }

    @Bean
    fun workerClock(): Clock = Clock.systemUTC()
}
