package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.SmsGateway
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SmsOtpDeliveryTest {
    @Test
    fun `sends customer OTP through configured SMS gateway`() {
        val gateway = mockk<SmsGateway>(relaxed = true)

        SmsOtpDelivery(gateway).deliver("+919876543210", "123456")

        verify(exactly = 1) {
            gateway.send("+919876543210", match { it.contains("123456") && it.contains("expires") })
        }
    }
}
