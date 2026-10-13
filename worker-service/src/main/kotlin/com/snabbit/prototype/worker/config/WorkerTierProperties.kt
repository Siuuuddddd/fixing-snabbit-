package com.snabbit.prototype.worker.config

import java.time.ZoneId
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("worker.tiers")
data class WorkerTierProperties(
    val veteranMonths: Long = 6,
    val expertMonths: Long = 9,
    val zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
) {
    init {
        require(veteranMonths > 0) { "worker.tiers.veteran-months must be positive" }
        require(expertMonths > veteranMonths) {
            "worker.tiers.expert-months must be greater than veteran-months"
        }
    }
}
