package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingDevSeedProperties
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus
import com.snabbit.prototype.booking.dto.request.WorkerLocationCommand
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import com.snabbit.prototype.platform.DevSeedWorkerIds
import java.math.BigDecimal
import java.time.Clock
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("local", "dev")
class DevSeedBookingAutomation(
    private val offers: WorkerOfferRepository,
    private val holds: SlotHoldRepository,
    private val bookings: BookingRepository,
    private val lifecycle: BookingLifecycleService,
    private val properties: BookingDevSeedProperties,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${dev-seed.automation-scan-delay-ms:500}")
    fun scan() {
        if (properties.autoAcceptOffers) autoAcceptOffers()
        if (properties.autoProgress) autoProgressVisits()
    }

    internal fun autoAcceptOffers() {
        val cutoff = clock.instant().minusMillis(properties.autoAcceptDelayMs)
        offers.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            WorkerOfferStatus.OFFERED,
            cutoff,
            PageRequest.of(0, 100),
        ).asSequence()
            .filter { it.workerId in DevSeedWorkerIds.all }
            .filter { offer -> holds.findById(offer.slotHoldId).orElse(null)?.assignmentType != com.snabbit.prototype.booking.domain.enums.AssignmentType.UNASSIGNED }
            .forEach { offer -> runCatching { lifecycle.acceptOffer(offer.bookingId, offer.id, offer.workerId) } }
    }

    internal fun autoProgressVisits() {
        val now = clock.instant()
        holds.findByWorkerIdInAndStatusAndJobStatusIn(
            DevSeedWorkerIds.all,
            SlotHoldStatus.CONFIRMED,
            listOf(JobStatus.ASSIGNED, JobStatus.EN_ROUTE),
        ).forEach { hold ->
            val booking = bookings.findById(hold.bookingId).orElse(null) ?: return@forEach
            val confirmedAt = booking.confirmedAt ?: return@forEach
            if (booking.status != BookingStatus.CONFIRMED) return@forEach
            when (hold.jobStatus) {
                JobStatus.ASSIGNED -> if (!confirmedAt.plusMillis(properties.autoProgressEnRouteDelayMs).isAfter(now)) {
                    runCatching { lifecycle.workerEnRoute(booking.id, hold.visitIndex, hold.workerId) }
                }
                JobStatus.EN_ROUTE -> if (!confirmedAt.plusMillis(properties.autoProgressArrivalDelayMs).isAfter(now)) {
                    val latitude = booking.serviceLatitude ?: return@forEach
                    val longitude = booking.serviceLongitude ?: return@forEach
                    runCatching {
                        lifecycle.workerArrive(
                            booking.id,
                            hold.visitIndex,
                            hold.workerId,
                            WorkerLocationCommand(latitude, longitude, BigDecimal.ONE),
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
