package com.snabbit.prototype.gateway

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec

@ConfigurationProperties("auth")
data class GatewayAuthProperties(
    val issuer: String,
    val jwtSecret: String,
)

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

        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/v1/auth/**", "/actuator/health/**", "/webhooks/razorpay").permitAll()
                    .pathMatchers("/api/v1/**").authenticated()
                    .anyExchange().permitAll()
            }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt { jwt -> jwt.jwtDecoder(decoder) } }
            .build()
    }
}

@Component
class AuthenticatedIdentityHeaderFilter : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> =
        exchange.getPrincipal<JwtAuthenticationToken>()
            .map { authentication -> mutate(exchange, authentication.token.subject) }
            .defaultIfEmpty(mutate(exchange, null))
            .flatMap(chain::filter)

    private fun mutate(exchange: ServerWebExchange, userId: String?): ServerWebExchange = exchange.mutate()
        .request { request ->
            request.headers { headers ->
                headers.remove(AUTHENTICATED_USER_HEADER)
                if (userId != null) headers.set(AUTHENTICATED_USER_HEADER, userId)
            }
        }
        .build()

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    companion object {
        const val AUTHENTICATED_USER_HEADER = "X-Authenticated-User-Id"
    }
}
