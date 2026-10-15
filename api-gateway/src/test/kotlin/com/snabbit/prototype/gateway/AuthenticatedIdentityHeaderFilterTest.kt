package com.snabbit.prototype.gateway

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

class AuthenticatedIdentityHeaderFilterTest {
    private val filter = AuthenticatedIdentityHeaderFilter()

    @Test
    fun `replaces spoofed identity header with authenticated JWT subject`() {
        val userId = UUID.randomUUID().toString()
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/bookings")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER, "spoofed")
                .build(),
        ).mutate().principal(Mono.just(authentication(userId))).build()
        val forwarded = invoke(exchange)

        assertEquals(userId, forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER))
        assertEquals(SESSION_ID, forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_SESSION_HEADER))
    }

    @Test
    fun `removes identity header from unauthenticated request`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/auth/google")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER, "spoofed")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_WORKER_HEADER, "spoofed-worker")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_HEADER, "spoofed-staff")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_ROLES_HEADER, "ADMIN")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_SESSION_HEADER, "spoofed-session")
                .build(),
        )
        val forwarded = invoke(exchange)

        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER))
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_WORKER_HEADER))
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_HEADER))
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_ROLES_HEADER))
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_SESSION_HEADER))
    }

    @Test
    fun `worker token creates only the trusted worker identity header`() {
        val workerId = UUID.randomUUID().toString()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/worker/bookings"))
            .mutate().principal(Mono.just(authentication(workerId, "WORKER"))).build()

        val forwarded = invoke(exchange)

        assertEquals(workerId, forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_WORKER_HEADER))
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER))
    }

    @Test
    fun `staff token creates trusted staff headers and removes spoofed values`() {
        val staffId = UUID.randomUUID().toString()
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/staff/support/tickets")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_HEADER, "spoofed")
                .header(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_ROLES_HEADER, "ADMIN")
                .build(),
        ).mutate().principal(Mono.just(authentication(staffId, "SUPPORT_AGENT"))).build()

        val forwarded = invoke(exchange)

        assertEquals(staffId, forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_HEADER))
        assertEquals(
            "SUPPORT_AGENT",
            forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_STAFF_ROLES_HEADER),
        )
        assertNull(forwarded.request.headers.getFirst(AuthenticatedIdentityHeaderFilter.AUTHENTICATED_USER_HEADER))
    }

    private fun invoke(exchange: ServerWebExchange): ServerWebExchange {
        val chain = mockk<GatewayFilterChain>()
        val forwarded = slot<ServerWebExchange>()
        every { chain.filter(capture(forwarded)) } returns Mono.empty()
        filter.filter(exchange, chain).block()
        return forwarded.captured
    }

    private fun authentication(subject: String, role: String = "CUSTOMER"): JwtAuthenticationToken {
        val now = Instant.parse("2026-10-02T04:00:00Z")
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("roles", listOf(role))
            .apply { if (role == "CUSTOMER" || role == "WORKER") claim("sid", SESSION_ID) }
            .build()
        return JwtAuthenticationToken(jwt)
    }

    companion object { private val SESSION_ID: String = UUID.randomUUID().toString() }
}
