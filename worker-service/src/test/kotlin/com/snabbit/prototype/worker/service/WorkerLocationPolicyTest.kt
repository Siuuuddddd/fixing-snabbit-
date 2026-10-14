package com.snabbit.prototype.worker.service

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerLocationPolicyTest {
    private val policy = WorkerLocationPolicy()

    @Test
    fun `worker inside both radii is eligible and distance is rounded to one decimal`() {
        val result = policy.evaluate(
            19.076090,
            72.877426,
            19.086090,
            72.877426,
            requestRadiusKm = 15.0,
            workerRadiusKm = 5.0,
        )

        assertTrue(result.inRange)
        assertEquals(BigDecimal("1.1"), result.displayDistanceKm)
    }

    @Test
    fun `worker outside request radius is rejected`() {
        val result = policy.evaluate(
            19.076090,
            72.877426,
            19.200000,
            72.877426,
            requestRadiusKm = 10.0,
            workerRadiusKm = 50.0,
        )

        assertFalse(result.inRange)
    }

    @Test
    fun `worker outside personal radius is rejected`() {
        val result = policy.evaluate(
            19.076090,
            72.877426,
            19.086090,
            72.877426,
            requestRadiusKm = 15.0,
            workerRadiusKm = 1.0,
        )

        assertFalse(result.inRange)
    }
}
