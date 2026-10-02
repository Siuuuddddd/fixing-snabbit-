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
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
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

@Component
class AuthenticatedIdentityHeaderFilter : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> =
        exchange.getPrincipal<JwtAuthenticationToken>()
            .map { authentication -> mutate(exchange, authentication) }
            .defaultIfEmpty(mutate(exchange, null))
            .flatMap(chain::filter)

    private fun mutate(exchange: ServerWebExchange, authentication: JwtAuthenticationToken?): ServerWebExchange = exchange.mutate()
        .request { request ->
            request.headers { headers ->
                headers.remove(AUTHENTICATED_USER_HEADER)
                headers.remove(AUTHENTICATED_WORKER_HEADER)
                headers.remove(AUTHENTICATED_STAFF_HEADER)
                headers.remove(AUTHENTICATED_STAFF_ROLES_HEADER)
                val roles = authentication?.token?.getClaimAsStringList("roles").orEmpty()
                if ("CUSTOMER" in roles) headers.set(AUTHENTICATED_USER_HEADER, authentication!!.token.subject)
                if ("WORKER" in roles) headers.set(AUTHENTICATED_WORKER_HEADER, authentication!!.token.subject)
                if (roles.any(STAFF_ROLES::contains)) {
                    headers.set(AUTHENTICATED_STAFF_HEADER, authentication!!.token.subject)
                    headers.set(AUTHENTICATED_STAFF_ROLES_HEADER, roles.filter(STAFF_ROLES::contains).joinToString(","))
                }
            }
        }
        .build()

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    companion object {
        const val AUTHENTICATED_USER_HEADER = "X-Authenticated-User-Id"
        const val AUTHENTICATED_WORKER_HEADER = "X-Authenticated-Worker-Id"
        const val AUTHENTICATED_STAFF_HEADER = "X-Authenticated-Staff-Id"
        const val AUTHENTICATED_STAFF_ROLES_HEADER = "X-Authenticated-Staff-Roles"
        val STAFF_ROLES = setOf("SUPPORT_AGENT", "OPERATIONS_MANAGER", "ADMIN", "FINANCE_OPERATOR")
    }
}
