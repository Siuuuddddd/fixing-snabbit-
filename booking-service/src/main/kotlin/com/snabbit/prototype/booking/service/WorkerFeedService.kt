package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.catalog.ServiceOfferingRepository
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus
import com.snabbit.prototype.booking.dto.response.WorkerJobFeedItem
import com.snabbit.prototype.booking.dto.response.WorkerOfferFeedItem
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerFeedService(
    private val offers: WorkerOfferRepository,
    private val holds: SlotHoldRepository,
    private val bookings: BookingRepository,
    private val services: ServiceOfferingRepository,
    private val arrivalChallenges: ArrivalChallengeService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun pendingOffers(workerId: UUID): List<WorkerOfferFeedItem> {
        val now = clock.instant()
        val openOffers = offers.findByWorkerIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
            workerId,
            WorkerOfferStatus.OFFERED,
            now,
            FEED_LIMIT,
        )
        val holdsById = holds.findAllById(openOffers.map { it.slotHoldId }).associateBy { it.id }
        val bookingsById = bookings.findAllById(openOffers.map { it.bookingId }).associateBy { it.id }
        val servicesById = services.findAllById(holdsById.values.map { it.serviceId }).associateBy { it.id }

        return openOffers.mapNotNull { offer ->
            val hold = holdsById[offer.slotHoldId]
                ?.takeIf { it.workerId == workerId && it.status == SlotHoldStatus.HELD && it.isActive(now) }
                ?: return@mapNotNull null
            val booking = bookingsById[offer.bookingId] ?: return@mapNotNull null
            val service = servicesById[hold.serviceId] ?: return@mapNotNull null
            WorkerOfferFeedItem(
                offer.id,
                offer.bookingId,
                hold.visitIndex,
                hold.serviceId,
                service.code,
                service.displayName,
                hold.startsAt,
                hold.endsAt,
                hold.durationMinutes,
                offer.expiresAt,
                booking.requirements,
            )
        }
    }

    @Transactional(readOnly = true)
    fun assignedJobs(workerId: UUID): List<WorkerJobFeedItem> {
        val now = clock.instant()
        val assignedHolds = holds.findByWorkerIdAndStatusAndJobStatusInAndStartsAtAfterOrderByStartsAtAsc(
            workerId,
            SlotHoldStatus.CONFIRMED,
            ACTIVE_JOB_STATUSES,
            now.minus(JOB_LOOKBACK),
            FEED_LIMIT,
        )
        val bookingsById = bookings.findAllById(assignedHolds.map { it.bookingId }).associateBy { it.id }
        val servicesById = services.findAllById(assignedHolds.map { it.serviceId }).associateBy { it.id }

        return assignedHolds.mapNotNull { hold ->
            val booking = bookingsById[hold.bookingId] ?: return@mapNotNull null
            val service = servicesById[hold.serviceId] ?: return@mapNotNull null
            val revealLocation = !now.isBefore(hold.startsAt.minus(LOCATION_REVEAL_WINDOW))
            val otpStatus = arrivalChallenges.statusFor(hold.id)
            WorkerJobFeedItem(
                hold.bookingId,
                hold.visitIndex,
                hold.serviceId,
                service.code,
                service.displayName,
                hold.startsAt,
                hold.endsAt,
                hold.durationMinutes,
                requireNotNull(hold.jobStatus),
                booking.requirements,
                booking.serviceLatitude.takeIf { revealLocation },
                booking.serviceLongitude.takeIf { revealLocation },
                booking.serviceAddressLabel.takeIf { revealLocation },
                booking.serviceAddressLine.takeIf { revealLocation },
                booking.serviceAddressPincode.takeIf { revealLocation },
                otpStatus?.name,
                otpStatus?.displayText,
            )
        }
    }

    private companion object {
        val FEED_LIMIT: PageRequest = PageRequest.of(0, 100)
        val JOB_LOOKBACK: Duration = Duration.ofHours(12)
        val LOCATION_REVEAL_WINDOW: Duration = Duration.ofHours(2)
        val ACTIVE_JOB_STATUSES = setOf(JobStatus.ASSIGNED, JobStatus.ARRIVED, JobStatus.IN_PROGRESS)
    }
}
