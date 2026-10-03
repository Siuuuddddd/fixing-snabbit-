package com.snabbit.prototype.support.security

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ServiceAuthWebFilterTest {
    private val token = "test-service-token-with-at-least-32-bytes"
    private val filter = ServiceAuthWebFilter(ServiceAuthProperties(token))

    @Test
    fun `reactive internal route rejects missing service identity`() {
        val invoked = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/v1/support/tickets/1").build())

        filter.filter(exchange, chain(invoked)).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
        assertFalse(invoked.get())
    }

    @Test
    fun `reactive internal route accepts matching service identity`() {
        val invoked = AtomicBoolean(false)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/v1/support/tickets/1")
                .header(ServiceAuthWebFilter.HEADER, token)
                .build(),
        )

        filter.filter(exchange, chain(invoked)).block()

        assertTrue(invoked.get())
    }

    private fun chain(invoked: AtomicBoolean) = WebFilterChain {
        invoked.set(true)
        Mono.empty()
    }
}
