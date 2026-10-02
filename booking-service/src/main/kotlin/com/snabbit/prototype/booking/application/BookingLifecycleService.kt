package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingStatus
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.JobStatus
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
                serviceLatitude = command.serviceLatitude,
                serviceLongitude = command.serviceLongitude,
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

    @Transactional(readOnly = true)
    fun paymentQuote(bookingId: UUID): PaymentQuoteSnapshot {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        val holds = requireHolds(bookingId)
        return PaymentQuoteSnapshot(
            bookingId = booking.id,
            customerId = booking.customerId,
            status = booking.status.name,
            quotedTotalMinor = booking.quotedTotalMinor,
            currency = "INR",
            holdExpiresAt = holds.minOf(SlotHold::expiresAt),
        )
    }

    @Transactional(readOnly = true)
    fun ratingContext(bookingId: UUID): RatingContextSnapshot {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        val holds = requireHolds(bookingId)
        return RatingContextSnapshot(
            booking.id,
            booking.customerId,
            holds.map { hold ->
                RatingVisitSnapshot(hold.visitIndex, hold.workerId, hold.jobStatus == JobStatus.COMPLETED)
            },
        )
    }

    @Transactional(readOnly = true)
    fun ownership(bookingId: UUID): BookingOwnershipSnapshot {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        return BookingOwnershipSnapshot(booking.id, booking.customerId)
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
    fun cancel(bookingId: UUID, expectedCustomerId: UUID? = null): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (expectedCustomerId != null && booking.customerId != expectedCustomerId) {
            throw com.snabbit.prototype.booking.support.CustomerAccessDenied()
        }
        if (booking.status == BookingStatus.CANCELLED) return currentSnapshot(booking)
        if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.COMPLETED) {
            throw InvalidBookingState("Confirmed or completed bookings require the cancellation and refund workflow")
        }
        booking.cancel(now)
        requireHolds(bookingId).forEach(SlotHold::release)
        closeRemainingOffers(bookingId, now)
        outboxWriter.append(bookingId, "BookingCancelled", mapOf("bookingId" to bookingId), now)
        return currentSnapshot(booking)
    }

    @Transactional
    fun workerArrive(
        bookingId: UUID,
        visitIndex: Int,
        workerId: UUID,
        location: WorkerLocationCommand,
    ): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val hold = requireWorkerVisit(booking, visitIndex, workerId)
        if (booking.status == BookingStatus.COMPLETED && hold.jobStatus == JobStatus.COMPLETED) {
            return currentSnapshot(booking)
        }
        if (hold.jobStatus?.name == "ARRIVED") return currentSnapshot(booking)
        validateArrivalLocation(booking, location)
        try {
            hold.arrive(location.latitude, location.longitude, location.accuracyMeters, now)
        } catch (error: IllegalStateException) {
            throw InvalidBookingState(error.message.orEmpty())
        }
        outboxWriter.append(
            bookingId,
            "WorkerArrived",
            mapOf("bookingId" to bookingId, "visitIndex" to visitIndex, "workerId" to workerId, "arrivedAt" to now),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun workerStart(bookingId: UUID, visitIndex: Int, workerId: UUID): BookingSnapshot =
        transitionWorkerVisit(bookingId, visitIndex, workerId, "ServiceStarted") { hold, now -> hold.start(now) }

    @Transactional
    fun workerComplete(bookingId: UUID, visitIndex: Int, workerId: UUID): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val hold = requireWorkerVisit(booking, visitIndex, workerId)
        if (booking.status == BookingStatus.COMPLETED && hold.jobStatus == JobStatus.COMPLETED) {
            return currentSnapshot(booking)
        }
        val before = hold.jobStatus
        try {
            hold.complete(now)
        } catch (error: IllegalStateException) {
            throw InvalidBookingState(error.message.orEmpty())
        }
        if (hold.jobStatus != before) {
            outboxWriter.append(
                bookingId,
                "ServiceCompleted",
                mapOf("bookingId" to bookingId, "visitIndex" to visitIndex, "workerId" to workerId, "occurredAt" to now),
                now,
            )
        }
        val holds = requireHolds(bookingId)
        if (booking.status == BookingStatus.CONFIRMED && holds.all { it.jobStatus == JobStatus.COMPLETED }) {
            booking.complete(now)
            outboxWriter.append(bookingId, "BookingCompleted", mapOf("bookingId" to bookingId, "completedAt" to now), now)
        }
        return snapshot(booking, holds, offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId))
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

    private fun requireWorkerVisit(booking: Booking, visitIndex: Int, workerId: UUID): SlotHold {
        if (booking.status != BookingStatus.CONFIRMED && booking.status != BookingStatus.COMPLETED) {
            throw InvalidBookingState("Booking is not confirmed")
        }
        val hold = slotHoldRepository.findVisitForUpdate(booking.id, visitIndex)
            ?: throw InvalidBookingState("Booking visit does not exist")
        if (hold.workerId != workerId) throw OfferUnavailable("Booking visit belongs to a different worker")
        return hold
    }

    private fun transitionWorkerVisit(
        bookingId: UUID,
        visitIndex: Int,
        workerId: UUID,
        eventType: String,
        transition: (SlotHold, Instant) -> Unit,
    ): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val hold = requireWorkerVisit(booking, visitIndex, workerId)
        val before = hold.jobStatus
        try {
            transition(hold, now)
        } catch (error: IllegalStateException) {
            throw InvalidBookingState(error.message.orEmpty())
        }
        if (hold.jobStatus != before) {
            outboxWriter.append(
                bookingId,
                eventType,
                mapOf("bookingId" to bookingId, "visitIndex" to visitIndex, "workerId" to workerId, "occurredAt" to now),
                now,
            )
        }
        return currentSnapshot(booking)
    }

    private fun validateArrivalLocation(booking: Booking, location: WorkerLocationCommand) {
        if (location.latitude.toDouble() !in -90.0..90.0 || location.longitude.toDouble() !in -180.0..180.0) {
            throw BookingRuleViolation("Valid worker coordinates are required")
        }
        if (location.accuracyMeters <= java.math.BigDecimal.ZERO ||
            location.accuracyMeters.toDouble() > properties.arrivalAccuracyMeters
        ) {
            throw BookingRuleViolation("Location accuracy must be within ${properties.arrivalAccuracyMeters.toInt()} metres")
        }
        val serviceLatitude = booking.serviceLatitude
            ?: throw InvalidBookingState("Booking has no verified service location")
        val serviceLongitude = booking.serviceLongitude
            ?: throw InvalidBookingState("Booking has no verified service location")
        val distanceMeters = distanceMeters(
            serviceLatitude.toDouble(),
            serviceLongitude.toDouble(),
            location.latitude.toDouble(),
            location.longitude.toDouble(),
        )
        if (distanceMeters > properties.arrivalRadiusMeters) {
            throw BookingRuleViolation("Worker must be within ${properties.arrivalRadiusMeters.toInt()} metres of the service address")
        }
    }

    private fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val latitudeDistance = Math.toRadians(toLat - fromLat)
        val longitudeDistance = Math.toRadians(toLon - fromLon)
        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)
        return 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

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
            customerId = booking.customerId,
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
                    jobStatus = hold.jobStatus?.name,
                    arrivedAt = hold.arrivedAt,
                    startedAt = hold.startedAt,
                    completedAt = hold.completedAt,
                )
            },
        )
    }
}
