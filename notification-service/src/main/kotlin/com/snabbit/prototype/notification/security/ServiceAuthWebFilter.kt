package com.snabbit.prototype.notification.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class ServiceAuthWebFilter(private val properties: ServiceAuthProperties) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!exchange.request.path.value().startsWith("/internal/v1/")) return chain.filter(exchange)
        val supplied = exchange.request.headers.getFirst(HEADER)
        val valid = supplied != null && MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            properties.token.toByteArray(StandardCharsets.UTF_8),
        )
        if (!valid) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }
        return chain.filter(exchange)
    }

    companion object {
        const val HEADER = "X-Service-Token"
    }
}
