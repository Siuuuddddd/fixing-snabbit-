package com.snabbit.prototype.wallet.payment

import com.snabbit.prototype.wallet.config.WalletProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class RazorpayWebhookVerifier(private val properties: WalletProperties) {
    fun verify(rawBody: ByteArray, providedSignature: String): Boolean {
        if (providedSignature.isBlank() || properties.razorpay.webhookSecret.isBlank()) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.razorpay.webhookSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(rawBody).toHex().toByteArray(StandardCharsets.US_ASCII)
        val provided = providedSignature.lowercase().toByteArray(StandardCharsets.US_ASCII)
        return MessageDigest.isEqual(expected, provided)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
