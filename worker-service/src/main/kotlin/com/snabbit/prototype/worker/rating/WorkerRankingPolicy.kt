package com.snabbit.prototype.worker.rating

import com.snabbit.prototype.worker.domain.WorkerProfile
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class WorkerRankingPolicy(private val properties: WorkerRatingProperties) {
    fun score(worker: WorkerProfile): BigDecimal =
        properties.priorMean.multiply(BigDecimal(properties.priorWeight))
            .add(BigDecimal(worker.ratingSum))
            .divide(BigDecimal(properties.priorWeight + worker.ratingCount), 4, RoundingMode.HALF_UP)
}
