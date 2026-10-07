package com.snabbit.prototype.platform

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TwilioVerifyProviderTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = TwilioVerifyProvider(builder, OtpProviderProperties(
        accountSid = "AC00000000000000000000000000000000",
        authToken = "secret",
        verifyServiceSid = "VA00000000000000000000000000000000",
    ), ObjectMapper())

    @Test
    fun `starts sms verification`() {
        server.expect(requestTo("https://verify.twilio.com/v2/Services/VA00000000000000000000000000000000/Verifications"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON))
        assertEquals(300, provider.start("+919876543210").expiresInSeconds)
        server.verify()
    }

    @Test
    fun `maps approved and wrong codes`() {
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON))
        server.expect(method(HttpMethod.POST)).andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON))
        assertEquals(VerificationStatus.APPROVED, provider.check("+919876543210", "123456"))
        assertEquals(VerificationStatus.WRONG_CODE, provider.check("+919876543210", "000000"))
    }

    @Test
    fun `maps expired max attempts and trial restrictions`() {
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON).body("{\"code\":20404}"))
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON).body("{\"code\":60202}"))
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON).body("{\"code\":21608}"))
        assertEquals(VerificationStatus.EXPIRED, provider.check("+919876543210", "123456"))
        assertEquals(VerificationStatus.MAX_ATTEMPTS, provider.check("+919876543210", "123456"))
        assertThrows(UnverifiedTrialRecipient::class.java) { provider.start("+919876543210") }
    }
}
