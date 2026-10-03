package com.snabbit.prototype.booking.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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
