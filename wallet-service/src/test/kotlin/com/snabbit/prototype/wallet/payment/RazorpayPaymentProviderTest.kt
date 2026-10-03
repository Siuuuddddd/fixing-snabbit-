package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
            RazorpayProperties("test", "https://api.razorpay.com", "key", "secret", "webhook"),
        ),
    )

    @Test
    fun `finds an existing Razorpay order by exact receipt`() {
        val receipt = "booking_0123456789abcdef0123456789abcdef"
        server.expect(requestTo("https://api.razorpay.com/v1/orders?receipt=$receipt&count=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"count":1,"items":[{"id":"order_existing","amount":12500,"currency":"INR","receipt":"$receipt"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            ProviderOrder("order_existing", 12_500, "INR"),
            provider.findOrderByReceipt(receipt),
        )
        server.verify()
    }

    @Test
    fun `ignores partial receipt matches returned by Razorpay`() {
        val receipt = "plus_0123456789abcdef0123456789abcdef"
        server.expect(requestTo("https://api.razorpay.com/v1/orders?receipt=$receipt&count=2"))
            .andRespond(
                withSuccess(
                    """{"count":1,"items":[{"id":"order_other","amount":12500,"currency":"INR","receipt":"${receipt}_other"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertNull(provider.findOrderByReceipt(receipt))
        server.verify()
    }

    @Test
    fun `rejects receipts that exceed the provider limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            provider.createOrder(12_500, "x".repeat(41))
        }
    }

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

    @Test
    fun `fake provider makes order creation recoverable by receipt`() {
        val fake = RazorpayPaymentProvider(
            RestClient.builder(),
            WalletProperties(
                "INR", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(5),
                RazorpayProperties("fake", "https://example.invalid", "", "", "webhook"),
            ),
        )
        val receipt = "topup_0123456789abcdef0123456789abcdef"

        val first = fake.createOrder(12_500, receipt)
        val replay = fake.createOrder(12_500, receipt)

        assertEquals(first, replay)
        assertEquals(first, fake.findOrderByReceipt(receipt))
    }
}
