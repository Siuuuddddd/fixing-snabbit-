package com.snabbit.prototype.user.location

import com.snabbit.prototype.user.dto.response.GeocodedAddress

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class AddressLocationVerifierTest {
    private val provider = mockk<GeocodingProvider>()
    private val verifier = AddressLocationVerifier(
        provider,
        GeocodingProperties("google", apiKey = "test", maximumMismatchMeters = 250.0),
    )

    @Test
    fun `accepts GPS close to forward and reverse geocoded Mumbai address`() {
        val resolved = GeocodedAddress(
            BigDecimal("19.0760"), BigDecimal("72.8777"), "Mumbai", "Mumbai", "400001",
        )
        every { provider.geocode(any(), "Mumbai", "400001") } returns resolved
        every { provider.reverseGeocode(BigDecimal("19.0761"), BigDecimal("72.8778")) } returns resolved

        assertDoesNotThrow {
            verifier.verify(
                "1 Example Road", "Mumbai", "400001", BigDecimal("19.0761"), BigDecimal("72.8778"),
            )
        }
    }

    @Test
    fun `rejects GPS far from entered address`() {
        val entered = GeocodedAddress(
            BigDecimal("19.0760"), BigDecimal("72.8777"), "South Mumbai", "Mumbai", "400001",
        )
        val captured = GeocodedAddress(
            BigDecimal("19.2183"), BigDecimal("72.9781"), "Thane", "Mumbai", "400001",
        )
        every { provider.geocode(any(), "Mumbai", "400001") } returns entered
        every { provider.reverseGeocode(BigDecimal("19.2183"), BigDecimal("72.9781")) } returns captured

        assertThrows<LocationVerificationRejected> {
            verifier.verify(
                "1 Example Road", "Mumbai", "400001", BigDecimal("19.2183"), BigDecimal("72.9781"),
            )
        }
    }
}
