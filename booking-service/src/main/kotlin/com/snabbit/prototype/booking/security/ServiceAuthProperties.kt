package com.snabbit.prototype.booking.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.filter.OncePerRequestFilter
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
