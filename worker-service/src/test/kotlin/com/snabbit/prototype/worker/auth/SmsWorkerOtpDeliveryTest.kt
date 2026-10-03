package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.SmsGateway
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SmsWorkerOtpDeliveryTest {
    @Test
    fun `sends worker OTP through configured SMS gateway`() {
        val gateway = mockk<SmsGateway>(relaxed = true)

        SmsWorkerOtpDelivery(gateway).deliver("+919900000001", "654321")

        verify(exactly = 1) {
            gateway.send("+919900000001", match { it.contains("654321") && it.contains("worker") })
        }
    }
}
