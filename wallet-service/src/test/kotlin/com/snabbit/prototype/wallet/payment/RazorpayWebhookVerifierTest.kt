package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RazorpayWebhookVerifierTest {
    private val secret = "test-webhook-secret"
    private val verifier = RazorpayWebhookVerifier(
        WalletProperties(
            currency = "INR",
            reservationDuration = Duration.ofMinutes(5),
            refundRetryDelay = Duration.ofMinutes(1),
            razorpay = RazorpayProperties("fake", "https://example.invalid", "", "", secret),
        ),
    )

    @Test
    fun `accepts the exact raw body signature`() {
        val body = "{\"event\":\"payment.captured\",\"amount\":100}".toByteArray()

        assertTrue(verifier.verify(body, signature(body)))
    }

    @Test
    fun `rejects a changed body and invalid signature`() {
        val body = "{\"event\":\"payment.captured\"}".toByteArray()

        assertFalse(verifier.verify("{}".toByteArray(), signature(body)))
        assertFalse(verifier.verify(body, "not-a-signature"))
    }

    private fun signature(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }
}
