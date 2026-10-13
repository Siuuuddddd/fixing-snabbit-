package com.snabbit.prototype.worker.rating

import java.math.BigDecimal
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("worker.ratings")
data class WorkerRatingProperties(
    val updateWindow: Duration = Duration.ofHours(24),
    val priorMean: BigDecimal = BigDecimal("4.5"),
    val priorWeight: Int = 20,
) {
    init {
        require(!updateWindow.isNegative && !updateWindow.isZero) { "Rating update window must be positive" }
        require(priorMean >= BigDecimal.ONE && priorMean <= BigDecimal("5")) { "Rating prior mean must be 1 to 5" }
        require(priorWeight > 0) { "Rating prior weight must be positive" }
    }
}
