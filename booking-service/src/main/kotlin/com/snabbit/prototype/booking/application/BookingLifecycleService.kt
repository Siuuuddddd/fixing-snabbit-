package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingStatus
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.SlotHoldStatus
import com.snabbit.prototype.booking.domain.WorkerDayCapacity
import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.domain.WorkerOfferStatus
import com.snabbit.prototype.booking.persistence.BookingRepository
import com.snabbit.prototype.booking.persistence.SlotHoldRepository
import com.snabbit.prototype.booking.persistence.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.persistence.WorkerOfferRepository
import com.snabbit.prototype.booking.support.BookingNotFound
import com.snabbit.prototype.booking.support.BookingRuleViolation
import com.snabbit.prototype.booking.support.InvalidBookingState
import com.snabbit.prototype.booking.support.OfferUnavailable
import com.snabbit.prototype.booking.support.SlotUnavailable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class BookingLifecycleService(
    private val bookingRepository: BookingRepository,
    private val capacityRepository: WorkerDayCapacityRepository,
    private val slotHoldRepository: SlotHoldRepository,
    private val offerRepository: WorkerOfferRepository,
    private val bookingWindowPolicy: BookingWindowPolicy,
    private val outboxWriter: OutboxWriter,
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    @Transactional
    fun create(command: CreateBookingCommand): BookingSnapshot {
        validateVisitCollection(command)
        val now = clock.instant()
        val bookingId = UUID.randomUUID()

        val capacities = lockCapacities(command)
        validateInternalOverlaps(command)
        command.visits.forEach { visit ->
            bookingWindowPolicy.validate(command.bookingType, visit.startsAt, command.duration)
            validateCapacityAndOverlap(visit, command.duration.toMinutes(), capacities, now)
        }

        val booking = bookingRepository.saveAndFlush(
            Booking(
                id = bookingId,
                customerId = command.customerId,
                serviceId = command.serviceId,
                bookingType = command.bookingType,
                status = BookingStatus.AWAITING_WORKER,
                durationMinutes = command.duration.toMinutes().toInt(),
                quotedTotalMinor = command.quotedTotalMinor,
                requirements = command.requirements,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val holds = command.visits.mapIndexed { index, visit ->
            SlotHold(
                id = UUID.randomUUID(),
                bookingId = booking.id,
                visitIndex = index,
                customerId = booking.customerId,
                workerId = visit.workerId,
                serviceId = booking.serviceId,
                startsAt = visit.startsAt,
                endsAt = visit.startsAt.plus(command.duration),
                status = SlotHoldStatus.HELD,
                expiresAt = now.plus(properties.slotHoldDuration),
                createdAt = now,
            )
        }
        slotHoldRepository.saveAll(holds)

        val offers = holds.map { hold ->
            WorkerOffer(
                id = UUID.randomUUID(),
                bookingId = booking.id,
                slotHoldId = hold.id,
                workerId = hold.workerId,
                status = WorkerOfferStatus.OFFERED,
                expiresAt = now.plus(properties.workerOfferDuration),
                createdAt = now,
            )
        }
        offerRepository.saveAll(offers)

        outboxWriter.append(
            booking.id,
            "BookingHeld",
            mapOf(
                "bookingId" to booking.id,
                "visitCount" to holds.size,
                "holdExpiresAt" to holds.first().expiresAt,
                "quotedTotalMinor" to booking.quotedTotalMinor,
            ),
            now,
        )
        outboxWriter.append(
            booking.id,
            "WorkerOffersCreated",
            mapOf(
                "bookingId" to booking.id,
                "offers" to offers.map { offer ->
                    mapOf(
                        "offerId" to offer.id,
                        "workerId" to offer.workerId,
                        "slotHoldId" to offer.slotHoldId,
                        "expiresAt" to offer.expiresAt,
                        "requirements" to command.requirements,
                    )
                },
            ),
            now,
        )
        return snapshot(booking, holds, offers)
    }

    @Transactional(readOnly = true)
    fun get(bookingId: UUID): BookingSnapshot {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        return snapshot(
            booking,
            slotHoldRepository.findAllByBookingIdOrderByVisitIndex(bookingId),
            offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId),
        )
    }

    @Transactional
    fun acceptOffer(bookingId: UUID, offerId: UUID, workerId: UUID): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val offer = offerRepository.findForUpdate(bookingId, offerId)
            ?: throw OfferUnavailable("Worker offer does not exist")
        if (offer.workerId != workerId) throw OfferUnavailable("Worker offer belongs to a different worker")
        if (offer.status == WorkerOfferStatus.ACCEPTED) return currentSnapshot(booking)
        if (booking.status != BookingStatus.AWAITING_WORKER) {
            throw InvalidBookingState("Booking is not awaiting worker acceptance")
        }
        if (offer.status != WorkerOfferStatus.OFFERED) throw OfferUnavailable("Worker offer is no longer open")
        if (!offer.expiresAt.isAfter(now)) throw OfferUnavailable("Worker offer has expired")

        offer.accept(now)
        outboxWriter.append(
            bookingId,
            "WorkerOfferAccepted",
            mapOf("bookingId" to bookingId, "offerId" to offer.id, "workerId" to workerId),
            now,
        )

        if (offerRepository.countByBookingIdAndStatusNot(bookingId, WorkerOfferStatus.ACCEPTED) == 0L) {
            booking.awaitPayment(now)
            outboxWriter.append(
                bookingId,
                "BookingAwaitingPayment",
                mapOf("bookingId" to bookingId, "holdExpiresAt" to requireHolds(bookingId).first().expiresAt),
                now,
            )
        }
        return currentSnapshot(booking)
    }

    @Transactional
    fun rejectOffer(bookingId: UUID, offerId: UUID, workerId: UUID): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.status != BookingStatus.AWAITING_WORKER) {
            throw InvalidBookingState("Booking is not awaiting worker acceptance")
        }
        val offer = offerRepository.findForUpdate(bookingId, offerId)
            ?: throw OfferUnavailable("Worker offer does not exist")
        if (offer.workerId != workerId) throw OfferUnavailable("Worker offer belongs to a different worker")
        if (offer.status != WorkerOfferStatus.OFFERED) throw OfferUnavailable("Worker offer is no longer open")
        if (!offer.expiresAt.isAfter(now)) throw OfferUnavailable("Worker offer has expired")

        offer.reject(now)
        closeRemainingOffers(bookingId, now)
        booking.markWorkerUnavailable(now)
        requireHolds(bookingId).forEach(SlotHold::release)
        outboxWriter.append(
            bookingId,
            "WorkerOfferRejected",
            mapOf("bookingId" to bookingId, "offerId" to offerId, "workerId" to workerId),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun confirmPayment(bookingId: UUID, paymentReference: String): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (paymentReference.isBlank()) throw BookingRuleViolation("Payment reference is required")
        if (booking.status == BookingStatus.CONFIRMED && booking.paymentReference == paymentReference) {
            return currentSnapshot(booking)
        }
        if (booking.status != BookingStatus.AWAITING_PAYMENT) {
            throw InvalidBookingState("Booking is not awaiting payment")
        }

        val holds = requireHolds(bookingId)
        if (holds.any { !it.isActive(now) }) {
            holds.forEach(SlotHold::expire)
            booking.expire(now)
            outboxWriter.append(bookingId, "BookingExpired", mapOf("bookingId" to bookingId), now)
            return currentSnapshot(booking)
        }

        holds.forEach(SlotHold::confirm)
        booking.confirm(paymentReference, now)
        outboxWriter.append(
            bookingId,
            "BookingConfirmed",
            mapOf("bookingId" to bookingId, "paymentReference" to paymentReference),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun cancel(bookingId: UUID): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.status == BookingStatus.CANCELLED) return currentSnapshot(booking)
        if (booking.status == BookingStatus.CONFIRMED) {
            throw InvalidBookingState("Confirmed bookings require the cancellation and refund workflow")
        }
        booking.cancel(now)
        requireHolds(bookingId).forEach(SlotHold::release)
        closeRemainingOffers(bookingId, now)
        outboxWriter.append(bookingId, "BookingCancelled", mapOf("bookingId" to bookingId), now)
        return currentSnapshot(booking)
    }

    private fun validateVisitCollection(command: CreateBookingCommand) {
        val count = command.visits.size
        when (command.bookingType) {
            BookingType.MULTI_DAY -> if (count !in 2..7) {
                throw BookingRuleViolation("Multi-day bookings require 2 to 7 visits")
            }
            BookingType.INSTANT, BookingType.SCHEDULED -> if (count != 1) {
                throw BookingRuleViolation("Instant and scheduled bookings require exactly one visit")
            }
        }
        if (command.bookingType == BookingType.MULTI_DAY) {
            val dates = command.visits.map { it.startsAt.atZone(properties.zoneId).toLocalDate() }
            if (dates.distinct().size != dates.size) {
                throw BookingRuleViolation("Multi-day visits must occur on different dates")
            }
        }
    }

    private fun lockCapacities(command: CreateBookingCommand): Map<Pair<UUID, LocalDate>, WorkerDayCapacity> =
        command.visits
            .map { it.workerId to it.startsAt.atZone(properties.zoneId).toLocalDate() }
            .distinct()
            .sortedWith(compareBy({ it.second }, { it.first.toString() }))
            .associateWith { (workerId, date) ->
                capacityRepository.findForUpdate(workerId, date)
                    ?: throw SlotUnavailable("Worker has no availability on $date")
            }

    private fun validateInternalOverlaps(command: CreateBookingCommand) {
        val visits = command.visits.sortedBy { it.startsAt }
        visits.forEachIndexed { index, current ->
            visits.drop(index + 1)
                .filter { it.workerId == current.workerId }
                .firstOrNull { it.startsAt < current.startsAt.plus(command.duration) }
                ?.let { throw BookingRuleViolation("Requested visits overlap for the same worker") }
        }
    }

    private fun validateCapacityAndOverlap(
        visit: VisitCommand,
        durationMinutes: Long,
        capacities: Map<Pair<UUID, LocalDate>, WorkerDayCapacity>,
        now: Instant,
    ) {
        val localStart = visit.startsAt.atZone(properties.zoneId)
        val endsAt = visit.startsAt.plusSeconds(durationMinutes * 60)
        val localEnd = endsAt.atZone(properties.zoneId)
        val capacity = capacities.getValue(visit.workerId to localStart.toLocalDate())
        if (localStart.toLocalTime() < capacity.availableFrom || localEnd.toLocalTime() > capacity.availableUntil) {
            throw SlotUnavailable("Requested visit is outside the worker's availability")
        }
        if (slotHoldRepository.hasActiveOverlap(visit.workerId, visit.startsAt, endsAt, now)) {
            throw SlotUnavailable("Requested worker slot is no longer available")
        }
    }

    private fun requireBookingForUpdate(bookingId: UUID): Booking =
        bookingRepository.findForUpdate(bookingId) ?: throw BookingNotFound(bookingId)

    private fun requireHolds(bookingId: UUID): List<SlotHold> =
        slotHoldRepository.findAllByBookingIdOrderByVisitIndex(bookingId)
            .ifEmpty { throw InvalidBookingState("Booking has no slot holds") }

    private fun currentSnapshot(booking: Booking): BookingSnapshot = snapshot(
        booking,
        requireHolds(booking.id),
        offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id),
    )

    private fun closeRemainingOffers(bookingId: UUID, now: Instant) {
        offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId)
            .filter { it.status == WorkerOfferStatus.OFFERED }
            .forEach { it.expire(now) }
    }

    private fun snapshot(
        booking: Booking,
        holds: List<SlotHold>,
        offers: List<WorkerOffer>,
    ): BookingSnapshot {
        val offersByHold = offers.associateBy(WorkerOffer::slotHoldId)
        return BookingSnapshot(
            bookingId = booking.id,
            status = booking.status.name,
            quotedTotalMinor = booking.quotedTotalMinor,
            requirements = booking.requirements,
            visits = holds.map { hold ->
                val offer = offersByHold.getValue(hold.id)
                VisitSnapshot(
                    visitIndex = hold.visitIndex,
                    workerId = hold.workerId,
                    startsAt = hold.startsAt,
                    endsAt = hold.endsAt,
                    holdExpiresAt = hold.expiresAt,
                    offerId = offer.id,
                    offerStatus = offer.status.name,
                    offerExpiresAt = offer.expiresAt,
                )
            },
        )
    }
}
