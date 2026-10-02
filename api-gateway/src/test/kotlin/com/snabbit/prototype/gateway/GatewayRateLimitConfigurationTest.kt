package com.snabbit.prototype.gateway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.time.Instant

class GatewayRateLimitConfigurationTest {
    private val resolver = GatewayRateLimitConfiguration().gatewayRateLimitKeyResolver()

    @Test
    fun `authenticated callers are limited by immutable JWT subject`() {
        val token = Jwt(
            "token",
            Instant.parse("2026-10-02T04:00:00Z"),
            Instant.parse("2026-10-02T04:15:00Z"),
            mapOf<String, Any>("alg" to "HS256"),
            mapOf<String, Any>("sub" to "40000000-0000-0000-0000-000000000001"),
        )
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/bookings/test"))
            .mutate()
            .principal(Mono.just(JwtAuthenticationToken(token)))
            .build()

        val key = resolver.resolve(exchange).block()

        assertEquals("subject:40000000-0000-0000-0000-000000000001", key)
    }

    @Test
    fun `unauthenticated callers are limited by direct network address`() {
        val request = MockServerHttpRequest.post("/api/v1/auth/otp/request")
            .remoteAddress(InetSocketAddress("203.0.113.10", 43210))
            .build()
        val exchange = MockServerWebExchange.from(request)

        val key = resolver.resolve(exchange).block()

        assertEquals("address:203.0.113.10", key)
    }
}
