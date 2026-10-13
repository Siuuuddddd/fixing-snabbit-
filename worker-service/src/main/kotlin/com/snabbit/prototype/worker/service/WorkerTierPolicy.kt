package com.snabbit.prototype.worker.service

import com.snabbit.prototype.worker.config.WorkerTierProperties
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Component

@Component
class WorkerTierPolicy(
    private val properties: WorkerTierProperties,
    private val clock: Clock,
) {
    fun currentTier(worker: WorkerProfile): WorkerTier = tierAt(worker.joinedAt, clock.instant())

    fun tierAt(joinedAt: Instant, at: Instant): WorkerTier {
        val joinedDate = joinedAt.atZone(properties.zoneId).toLocalDate()
        val currentDate = at.atZone(properties.zoneId).toLocalDate()
        val completedMonths = if (currentDate.isBefore(joinedDate)) {
            0
        } else {
            ChronoUnit.MONTHS.between(joinedDate, currentDate)
        }
        return when {
            completedMonths >= properties.expertMonths -> WorkerTier.EXPERT
            completedMonths >= properties.veteranMonths -> WorkerTier.VETERAN
            else -> WorkerTier.STANDARD
        }
    }

    fun completedMonths(worker: WorkerProfile): Long {
        val joinedDate = worker.joinedAt.atZone(properties.zoneId).toLocalDate()
        val currentDate = clock.instant().atZone(properties.zoneId).toLocalDate()
        return if (currentDate.isBefore(joinedDate)) 0 else ChronoUnit.MONTHS.between(joinedDate, currentDate)
    }
}
