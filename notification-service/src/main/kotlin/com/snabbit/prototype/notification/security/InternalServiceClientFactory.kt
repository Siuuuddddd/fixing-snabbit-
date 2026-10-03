package com.snabbit.prototype.notification.security

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
