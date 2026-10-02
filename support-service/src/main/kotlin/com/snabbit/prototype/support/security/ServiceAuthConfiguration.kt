package com.snabbit.prototype.support.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@ConfigurationProperties("service-auth")
data class ServiceAuthProperties(val token: String) {
    init {
        require(token.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "Service authentication token must contain at least 32 bytes"
        }
    }
}

@Component
class InternalServiceClientFactory(
    private val builder: RestClient.Builder,
    private val properties: ServiceAuthProperties,
) {
    fun create(baseUrl: String): RestClient = builder.clone()
        .baseUrl(baseUrl)
        .defaultHeader(ServiceAuthWebFilter.HEADER, properties.token)
        .build()
}

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

