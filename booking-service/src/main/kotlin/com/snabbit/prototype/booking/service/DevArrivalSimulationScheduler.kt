package com.snabbit.prototype.booking.service

import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "dev")
@ConditionalOnProperty(prefix = "dev-simulation", name = ["enabled"], havingValue = "true")
class DevArrivalSimulationScheduler(
    private val simulation: DevArrivalSimulationService,
    private val lifecycle: BookingLifecycleService,
    private val clock: Clock,
    @Value("\${dev.auto-complete-delay-seconds:180}") private val autoCompleteDelaySeconds: Long,
) {
    @Scheduled(fixedDelayString = "\${dev-simulation.scan-delay-ms:5000}")
    fun scan() {
        simulation.fireDue()
        val cutoff = clock.instant().minusSeconds(autoCompleteDelaySeconds)
        simulation.dueCompletions(cutoff).forEach { candidate ->
            runCatching { lifecycle.workerComplete(candidate.bookingId, candidate.visitIndex, candidate.workerId) }
        }
    }
}
