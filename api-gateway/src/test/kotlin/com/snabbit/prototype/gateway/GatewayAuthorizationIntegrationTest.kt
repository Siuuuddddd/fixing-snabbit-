package com.snabbit.prototype.gateway

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayAuthorizationIntegrationTest {
    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `finance route rejects customer operations and support roles`() {
        listOf("CUSTOMER", "OPERATIONS_MANAGER", "SUPPORT_AGENT", "WORKER").forEach { role ->
            request("/api/v1/staff/finance/refunds", role)
                .expectStatus().isForbidden
        }
    }

    @Test
    fun `staff queues are isolated by role`() {
        request("/api/v1/staff/support/tickets", "FINANCE_OPERATOR")
            .expectStatus().isForbidden
        request("/api/v1/staff/operations/bookings/${java.util.UUID.randomUUID()}", "SUPPORT_AGENT")
            .expectStatus().isForbidden
    }

    @Test
    fun `worker and customer routes reject each others tokens`() {
        request("/api/v1/workers/me/availability", "CUSTOMER")
            .expectStatus().isForbidden
        request("/api/v1/bookings/${java.util.UUID.randomUUID()}", "WORKER")
            .expectStatus().isForbidden
    }

    @Test
    fun `protected route rejects an unauthenticated request`() {
        client.get().uri("/api/v1/bookings/${java.util.UUID.randomUUID()}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `correct roles pass the gateway authorization boundary`() {
        assertAuthorized("/api/v1/staff/finance/refunds", "FINANCE_OPERATOR")
        assertAuthorized("/api/v1/workers/me/availability", "WORKER")
        assertAuthorized("/api/v1/bookings/${java.util.UUID.randomUUID()}", "CUSTOMER")
    }

    @Test
    fun `Google sign-in selection and refresh are public while logout stays role protected`() {
        listOf(
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",
            "/api/v1/worker-auth/google",
            "/api/v1/worker-auth/select-profile",
            "/api/v1/worker-auth/refresh",
        ).forEach { path ->
            val status = client.post().uri(path).exchange().returnResult(Void::class.java).status
            assertNotEquals(HttpStatus.UNAUTHORIZED, status)
            assertNotEquals(HttpStatus.FORBIDDEN, status)
        }

        client.post().uri("/api/v1/auth/logout").exchange().expectStatus().isUnauthorized
        client.mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_WORKER")))
            .post().uri("/api/v1/auth/logout").exchange().expectStatus().isForbidden
        client.post().uri("/api/v1/worker-auth/switch-profile").exchange().expectStatus().isUnauthorized
    }

    private fun request(path: String, role: String): WebTestClient.ResponseSpec = client
        .mutateWith(mockJwt().authorities(SimpleGrantedAuthority("ROLE_$role")))
        .get()
        .uri(path)
        .exchange()

    private fun assertAuthorized(path: String, role: String) {
        val status = request(path, role).returnResult(Void::class.java).status
        assertNotEquals(HttpStatus.UNAUTHORIZED, status)
        assertNotEquals(HttpStatus.FORBIDDEN, status)
    }
}
