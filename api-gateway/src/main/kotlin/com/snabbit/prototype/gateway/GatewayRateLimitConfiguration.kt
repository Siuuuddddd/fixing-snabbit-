package com.snabbit.prototype.gateway

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono

@Configuration
class GatewayRateLimitConfiguration {
    @Bean
    fun gatewayRateLimitKeyResolver(): KeyResolver = KeyResolver { exchange ->
        exchange.getPrincipal<JwtAuthenticationToken>()
            .map { principal -> "subject:${principal.token.subject}" }
            .switchIfEmpty(
                Mono.fromSupplier {
                    val address = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
                    "address:$address"
                },
            )
    }
}
