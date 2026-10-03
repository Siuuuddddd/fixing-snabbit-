package com.snabbit.prototype.notification.delivery

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class FcmPushDeliveryAdapterTest {
    @Test
    fun `delegates push delivery to the production FCM client`() {
        val client = mockk<FcmClient>(relaxed = true)
        val adapter = FcmPushDeliveryAdapter(client)

        adapter.send(listOf("token-1", "token-2"), "Booking confirmed", "Worker assigned")

        verify(exactly = 1) {
            client.send(listOf("token-1", "token-2"), "Booking confirmed", "Worker assigned")
        }
    }

    @Test
    fun `FCM HTTP client sends one authenticated v1 message per device`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val tokens = mockk<GoogleAccessTokenProvider>()
        io.mockk.every { tokens.accessToken() } returns "oauth-token"
        server.expect(requestTo("https://fcm.test/v1/projects/snabbit-test/messages:send"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer oauth-token"))
            .andExpect(
                content().json(
                    """{"message":{"token":"device-1","notification":{"title":"Confirmed","body":"Ready"}}}""",
                ),
            )
            .andRespond(withSuccess())
        val client = FcmHttpClient(
            builder, tokens, FcmProperties("snabbit-test", "service-account.json", "https://fcm.test"),
        )

        client.send(listOf("device-1"), "Confirmed", "Ready")

        server.verify()
    }
}
