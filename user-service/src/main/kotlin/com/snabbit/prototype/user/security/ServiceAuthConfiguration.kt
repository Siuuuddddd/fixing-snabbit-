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
        .defaultHeader(ServiceAuthFilter.HEADER, properties.token)
        .build()
}

@Component
class ServiceAuthFilter(private val properties: ServiceAuthProperties) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/internal/v1/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val supplied = request.getHeader(HEADER)
        val valid = supplied != null && MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            properties.token.toByteArray(StandardCharsets.UTF_8),
        )
        if (!valid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid service identity is required")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Service-Token"
    }
}

