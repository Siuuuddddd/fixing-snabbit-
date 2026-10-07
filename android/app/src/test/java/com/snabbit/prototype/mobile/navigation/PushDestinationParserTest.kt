package com.snabbit.prototype.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushDestinationParserTest {
    @Test
    fun `customer booking push opens booking route`() {
        assertEquals(
            PushDestination.CustomerBooking("booking-1"),
            PushDestinationParser.parse(
                mapOf("type" to "BookingConfirmed", "bookingId" to "booking-1"),
                AppRole.CUSTOMER,
            ),
        )
    }

    @Test
    fun `customer payment push opens wallet route`() {
        assertEquals(
            PushDestination.CustomerWallet,
            PushDestinationParser.parse(
                mapOf("type" to "WalletRefundCompleted", "bookingId" to "booking-1"),
                AppRole.CUSTOMER,
            ),
        )
    }

    @Test
    fun `worker push opens job detail route`() {
        assertEquals(
            PushDestination.WorkerJob("booking-1", 2),
            PushDestinationParser.parse(
                mapOf(
                    "type" to "WorkerOffersCreated",
                    "bookingId" to "booking-1",
                    "jobId" to "job-1",
                    "visitIndex" to "2",
                ),
                AppRole.WORKER,
            ),
        )
    }

    @Test
    fun `push without a route identifier is ignored`() {
        assertNull(PushDestinationParser.parse(mapOf("type" to "BookingConfirmed"), AppRole.CUSTOMER))
    }
}
