package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import com.snabbit.prototype.wallet.dto.response.ProviderRefund
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class RazorpayTestModeFlowTest {
    private val secret = "test-webhook-secret"
    private val properties = WalletProperties(
        "INR", Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(5),
        RazorpayProperties("test", "https://api.razorpay.test", "rzp_test_key", "test-secret", secret),
    )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = RazorpayPaymentProvider(builder, properties)

    @Test
    fun `test mode creates top-up and booking orders verifies capture and submits refund`() {
        expectOrder("topup_0123456789abcdef0123456789abcdef", "order_topup", 5_000)
        expectOrder("booking_0123456789abcdef0123456789abcd", "order_booking", 12_500)
        val refundReference = UUID.randomUUID().toString()
        server.expect(requestTo("https://api.razorpay.test/v1/payments/pay_booking/refund"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Refund-Idempotency", refundReference))
            .andExpect(content().json("""{"amount":12500,"notes":{"snabbit_refund_id":"$refundReference"}}"""))
            .andRespond(withSuccess("""{"id":"rfnd_booking","amount":12500}""", MediaType.APPLICATION_JSON))

        val topUp = provider.createOrder(5_000, "topup_0123456789abcdef0123456789abcdef")
        val booking = provider.createOrder(12_500, "booking_0123456789abcdef0123456789abcd")
        val webhookBody = """{"event":"payment.captured","payload":{"payment":{"entity":{"order_id":"order_booking"}}}}"""
            .toByteArray()
        val verified = RazorpayWebhookVerifier(properties).verify(webhookBody, signature(webhookBody))
        val refund = provider.refund("pay_booking", 12_500, refundReference)

        assertEquals("order_topup", topUp.id)
        assertEquals("order_booking", booking.id)
        assertTrue(verified)
        assertEquals(ProviderRefund("rfnd_booking", 12_500), refund)
        server.verify()
    }

    private fun expectOrder(receipt: String, orderId: String, amount: Long) {
        server.expect(requestTo("https://api.razorpay.test/v1/orders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Basic cnpwX3Rlc3Rfa2V5OnRlc3Qtc2VjcmV0"))
            .andExpect(content().json("""{"amount":$amount,"currency":"INR","receipt":"$receipt"}"""))
            .andRespond(
                withSuccess(
                    """{"id":"$orderId","amount":$amount,"currency":"INR"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
    }

    private fun signature(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }
}
