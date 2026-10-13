package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.config.WorkerTierProperties
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerTierPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-13T06:30:00Z")
    private val policy = WorkerTierPolicy(
        WorkerTierProperties(veteranMonths = 6, expertMonths = 9, zoneId = zone),
        Clock.fixed(now, zone),
    )

    @Test
    fun `worker at five months and twenty nine days is standard`() {
        assertEquals(WorkerTier.STANDARD, policy.tierAt(instant("2026-04-14"), now))
    }

    @Test
    fun `worker at exactly six calendar months is veteran`() {
        assertEquals(WorkerTier.VETERAN, policy.tierAt(instant("2026-04-13"), now))
    }

    @Test
    fun `worker at eight months and twenty nine days is veteran`() {
        assertEquals(WorkerTier.VETERAN, policy.tierAt(instant("2026-01-14"), now))
    }

    @Test
    fun `worker at exactly nine calendar months is expert`() {
        assertEquals(WorkerTier.EXPERT, policy.tierAt(instant("2026-01-13"), now))
    }

    @Test
    fun `tier changes when the evaluation date crosses the boundary`() {
        val joinedAt = instant("2026-04-13")
        assertEquals(
            WorkerTier.STANDARD,
            policy.tierAt(joinedAt, Instant.parse("2026-10-12T18:29:59Z")),
        )
        assertEquals(
            WorkerTier.VETERAN,
            policy.tierAt(joinedAt, Instant.parse("2026-10-12T18:30:00Z")),
        )
    }

    @Test
    fun `configured surcharge is returned for every tier`() {
        assertEquals(0, policy.surchargeMinor(WorkerTier.STANDARD))
        assertEquals(3_000, policy.surchargeMinor(WorkerTier.VETERAN))
        assertEquals(5_000, policy.surchargeMinor(WorkerTier.EXPERT))
    }

    private fun instant(localDate: String): Instant =
        java.time.LocalDate.parse(localDate).atStartOfDay(zone).toInstant()
}
