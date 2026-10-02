package com.snabbit.prototype.booking.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class ServiceAuthFilterTest {
    private val token = "test-service-token-with-at-least-32-bytes"
    private val filter = ServiceAuthFilter(ServiceAuthProperties(token))

    @Test
    fun `internal route rejects missing service identity`() {
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest("GET", "/internal/v1/bookings/123"), response, MockFilterChain())

        assertEquals(401, response.status)
    }

    @Test
    fun `internal route accepts matching service identity`() {
        val request = MockHttpServletRequest("GET", "/internal/v1/bookings/123").apply {
            addHeader(ServiceAuthFilter.HEADER, token)
        }
        val chain = MockFilterChain()

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertTrue(chain.request != null)
    }

    @Test
    fun `public routes do not require service credential and weak configured secrets are rejected`() {
        val chain = MockFilterChain()
        filter.doFilter(
            MockHttpServletRequest("GET", "/api/v1/bookings/123"),
            MockHttpServletResponse(),
            chain,
        )

        assertTrue(chain.request != null)
        assertThrows(IllegalArgumentException::class.java) { ServiceAuthProperties("too-short") }
    }

    @Test
    fun `service RestClient automatically sends configured identity`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val factory = InternalServiceClientFactory(builder, ServiceAuthProperties(token))
        server.expect(requestTo("https://service.test/internal/v1/resource"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(ServiceAuthFilter.HEADER, token))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.razorpay.test/orders"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(headerDoesNotExist(ServiceAuthFilter.HEADER))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        factory.create("https://service.test").get().uri("/internal/v1/resource").retrieve().toBodilessEntity()
        builder.build().get().uri("https://api.razorpay.test/orders").retrieve().toBodilessEntity()

        server.verify()
    }
}
