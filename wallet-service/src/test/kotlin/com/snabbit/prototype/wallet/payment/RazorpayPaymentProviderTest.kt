package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.UUID

class RazorpayPaymentProviderTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = RazorpayPaymentProvider(
        builder,
        WalletProperties(
            "INR", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(5),
            RazorpayProperties("real", "https://api.razorpay.com", "key", "secret", "webhook"),
        ),
    )

    @Test
    fun `sends the durable refund reference as Razorpay idempotency key`() {
        val reference = UUID.randomUUID().toString()
        server.expect(requestTo("https://api.razorpay.com/v1/payments/pay_1/refund"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Refund-Idempotency", reference))
            .andExpect(
                content().json(
                    """{"amount":12500,"notes":{"snabbit_refund_id":"$reference"}}""",
                ),
            )
            .andRespond(
            withSuccess(
                """{"id":"rfnd_existing","amount":12500}""",
                MediaType.APPLICATION_JSON,
            ),
        )

        val result = provider.refund("pay_1", 12_500, reference)

        assertEquals(ProviderRefund("rfnd_existing", 12_500), result)
        server.verify()
    }

    @Test
    fun `fake provider returns the same refund identity for a retry`() {
        val reference = UUID.randomUUID().toString()
        val fake = RazorpayPaymentProvider(
            RestClient.builder(),
            WalletProperties(
                "INR", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(5),
                RazorpayProperties("fake", "https://example.invalid", "", "", "webhook"),
            ),
        )

        val first = fake.refund("pay_2", 12_500, reference)
        val replay = fake.refund("pay_2", 12_500, reference)

        assertEquals(first, replay)
    }
}
