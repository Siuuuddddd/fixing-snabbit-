package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.exception.BookingNotFound
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("local", "dev")
class DevArrivalSimulationService(
    private val bookings: BookingRepository,
    private val holds: SlotHoldRepository,
    private val challenges: ArrivalChallengeService,
    private val outbox: OutboxWriter,
    private val clock: Clock,
) {
    @Transactional
    fun fireDue(limit: Int = 100) {
        val now = clock.instant()
        holds.findDueArrivalIds(now, limit).forEach { holdId ->
            val hold = holds.findForUpdate(holdId) ?: return@forEach
            val booking = bookings.findForUpdate(hold.bookingId) ?: throw BookingNotFound(hold.bookingId)
            if (booking.status != BookingStatus.CONFIRMED || hold.jobStatus != JobStatus.ASSIGNED ||
                hold.arrivalFiredAt != null || hold.arrivalDueAt?.isAfter(now) != false
            ) return@forEach
            val latitude = booking.serviceLatitude ?: return@forEach
            val longitude = booking.serviceLongitude ?: return@forEach
            hold.simulatedArrive(latitude, longitude, now)
            challenges.createForValidatedArrival(booking, hold, now)
            outbox.append(
                booking.id,
                "WorkerArrived",
                mapOf(
                    "bookingId" to booking.id,
                    "visitIndex" to hold.visitIndex,
                    "workerId" to hold.workerId,
                    "arrivedAt" to now,
                    "evidence" to "SIMULATED",
                ),
                now,
            )
        }
    }

    @Transactional(readOnly = true)
    fun dueCompletions(cutoff: Instant, limit: Int = 100): List<CompletionCandidate> =
        holds.findAutoCompleteCandidateIds(cutoff, limit).mapNotNull { id ->
            holds.findById(id).orElse(null)?.let { CompletionCandidate(it.bookingId, it.visitIndex, it.workerId) }
        }
}

data class CompletionCandidate(val bookingId: UUID, val visitIndex: Int, val workerId: UUID)
