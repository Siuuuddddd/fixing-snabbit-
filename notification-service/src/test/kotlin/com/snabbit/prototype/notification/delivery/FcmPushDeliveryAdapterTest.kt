package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

class FcmPushDeliveryAdapterTest {
    @Test
    fun `delegates push delivery to the production FCM client`() {
        val client = mockk<FcmClient>()
        val adapter = FcmPushDeliveryAdapter(client)
        val message = PushMessage(
            "Booking confirmed", "Worker assigned",
            mapOf("type" to "BookingConfirmed", "bookingId" to "booking-1"), "bookings",
        )
        every { client.send(any(), any()) } returns PushDeliveryResult()

        adapter.send(listOf("token-1", "token-2"), message)

        verify(exactly = 1) {
            client.send(listOf("token-1", "token-2"), message)
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
                    """{
                        "message": {
                            "token": "device-1",
                            "notification": {"title": "Confirmed", "body": "Ready"},
                            "data": {"type": "BookingConfirmed", "bookingId": "booking-1"},
                            "android": {"priority": "HIGH", "notification": {"channel_id": "bookings"}}
                        }
                    }""",
                ),
            )
            .andRespond(withSuccess())
        val client = FcmHttpClient(
            builder, tokens, FcmProperties("snabbit-test", "service-account.json", "https://fcm.test"), ObjectMapper(),
        )

        client.send(
            listOf("device-1"),
            PushMessage(
                "Confirmed", "Ready",
                mapOf("type" to "BookingConfirmed", "bookingId" to "booking-1"), "bookings",
            ),
        )

        server.verify()
    }

    @Test
    fun `FCM invalid registration is returned without retrying the delivery`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val tokens = mockk<GoogleAccessTokenProvider>()
        every { tokens.accessToken() } returns "oauth-token"
        server.expect(requestTo("https://fcm.test/v1/projects/snabbit-test/messages:send"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND).body(
                    """{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}""",
                ),
            )
        val client = FcmHttpClient(
            builder, tokens, FcmProperties("snabbit-test", "service-account.json", "https://fcm.test"), ObjectMapper(),
        )

        val result = client.send(
            listOf("invalid-device"),
            PushMessage("Offer", "Expired", mapOf("type" to "WorkerOfferExpired"), "jobs"),
        )

        assertEquals(setOf("invalid-device"), result.invalidTokens)
        server.verify()
    }
}
