package com.snabbit.prototype.user.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

@Configuration
class AuthConfiguration {
    @Bean
    fun jwtEncoder(properties: AuthProperties): JwtEncoder {
        require(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "JWT secret must contain at least 32 bytes"
        }
        val key = SecretKeySpec(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
    }
}
