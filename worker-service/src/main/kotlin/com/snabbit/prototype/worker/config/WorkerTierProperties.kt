package com.snabbit.prototype.worker.config

import java.time.ZoneId
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("worker.tiers")
data class WorkerTierProperties(
    val veteranMonths: Long = 6,
    val expertMonths: Long = 9,
    val zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
    val standardSurchargeMinor: Long = 0,
    val veteranSurchargeMinor: Long = 3_000,
    val expertSurchargeMinor: Long = 5_000,
) {
    init {
        require(veteranMonths > 0) { "worker.tiers.veteran-months must be positive" }
        require(expertMonths > veteranMonths) {
            "worker.tiers.expert-months must be greater than veteran-months"
        }
        require(listOf(standardSurchargeMinor, veteranSurchargeMinor, expertSurchargeMinor).all {
            it >= 0 && it % 100 == 0L
        }) { "worker tier surcharges must be non-negative whole-rupee minor units" }
    }
}
