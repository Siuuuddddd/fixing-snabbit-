package com.snabbit.prototype.platform

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TwilioSmsGatewayTest {
    @Test
    fun `sends an authenticated Twilio form request`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = TwilioSmsGateway(
            builder,
            SmsProperties("twilio", "https://api.twilio.test", "AC123", "secret", "+919900000000"),
        )
        server.expect(requestTo("https://api.twilio.test/2010-04-01/Accounts/AC123/Messages.json"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Basic QUMxMjM6c2VjcmV0"))
            .andExpect(content().string("To=%2B919876543210&From=%2B919900000000&Body=Your+code+is+123456"))
            .andRespond(withSuccess())

        gateway.send("+919876543210", "Your code is 123456")

        server.verify()
    }
}
