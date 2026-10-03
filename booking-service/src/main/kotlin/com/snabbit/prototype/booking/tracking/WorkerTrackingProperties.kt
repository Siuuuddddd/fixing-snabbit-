package com.snabbit.prototype.booking.tracking

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tracking")
data class WorkerTrackingProperties(
    val visibilityLeadTime: Duration = Duration.ofHours(2),
    val pollInterval: Duration = Duration.ofSeconds(5),
    val maxLocationAge: Duration = Duration.ofMinutes(2),
) {
    init {
        require(!visibilityLeadTime.isNegative) { "Tracking visibility lead time cannot be negative" }
        require(!pollInterval.isNegative && !pollInterval.isZero) { "Tracking poll interval must be positive" }
        require(!maxLocationAge.isNegative && !maxLocationAge.isZero) { "Tracking maximum location age must be positive" }
    }
}
