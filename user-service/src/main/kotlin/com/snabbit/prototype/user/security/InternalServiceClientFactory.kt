package com.snabbit.prototype.user.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class InternalServiceClientFactory(
    private val builder: RestClient.Builder,
    private val properties: ServiceAuthProperties,
) {
    fun create(baseUrl: String): RestClient = builder.clone()
        .baseUrl(baseUrl)
        .defaultHeader(ServiceAuthFilter.HEADER, properties.token)
        .build()
}
