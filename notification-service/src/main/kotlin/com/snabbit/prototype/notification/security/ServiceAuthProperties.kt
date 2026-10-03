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

@ConfigurationProperties("service-auth")
data class ServiceAuthProperties(val token: String) {
    init {
        require(token.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "Service authentication token must contain at least 32 bytes"
        }
    }
}
