package com.snabbit.prototype.gateway

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec

@Configuration
class GatewaySecurityConfiguration {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        properties: GatewayAuthProperties,
    ): SecurityWebFilterChain {
        require(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "JWT secret must contain at least 32 bytes"
        }
        val secretKey = SecretKeySpec(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer))
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        val authenticationConverter = ReactiveJwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                reactor.core.publisher.Flux.fromIterable(authorities.convert(jwt).orEmpty())
            }
        }

        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/worker-auth/**",
                        "/api/v1/staff-auth/**",
                        "/actuator/health/**",
                        "/webhooks/razorpay",
                    ).permitAll()
                    .pathMatchers("/api/v1/staff/support/**").hasAnyRole("SUPPORT_AGENT", "ADMIN")
                    .pathMatchers("/api/v1/staff/operations/**").hasAnyRole("OPERATIONS_MANAGER", "ADMIN")
                    .pathMatchers("/api/v1/staff/finance/**").hasAnyRole("FINANCE_OPERATOR", "ADMIN")
                    .pathMatchers("/api/v1/staff/**").denyAll()
                    .pathMatchers("/api/v1/worker/**", "/api/v1/workers/**").hasRole("WORKER")
                    .pathMatchers("/api/v1/**").hasRole("CUSTOMER")
                    .anyExchange().denyAll()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { jwt ->
                    jwt.jwtDecoder(decoder)
                    jwt.jwtAuthenticationConverter(authenticationConverter)
                }
            }
            .build()
    }
}
