package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.WorkerDayCapacity
import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.dto.request.CreateBookingCommand
import com.snabbit.prototype.booking.dto.request.VisitCommand
import com.snabbit.prototype.booking.dto.request.WorkerLocationCommand
import com.snabbit.prototype.booking.dto.response.BookingOwnershipSnapshot
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.CancellationContext
import com.snabbit.prototype.booking.dto.response.PaymentQuoteSnapshot
import com.snabbit.prototype.booking.dto.response.PlusPassBookingContext
import com.snabbit.prototype.booking.dto.response.RatingContextSnapshot
import com.snabbit.prototype.booking.dto.response.RatingVisitSnapshot
import com.snabbit.prototype.booking.dto.response.VisitSnapshot
import com.snabbit.prototype.booking.dto.response.VisitRecoveryContext
import com.snabbit.prototype.booking.domain.enums.AssignmentType
import com.snabbit.prototype.booking.exception.BookingNotFound
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.exception.OfferUnavailable
import com.snabbit.prototype.booking.exception.SlotUnavailable
import com.snabbit.prototype.booking.exception.AtomicSlotReservationFailed
import com.snabbit.prototype.booking.exception.ConcurrentVisitFailure
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
        val bookingId = command.bookingId

        if ((command.idempotencyKey == null) != (command.requestFingerprint == null)) {
            throw BookingRuleViolation("Booking idempotency key and fingerprint must be supplied together")
        }
        command.idempotencyKey?.let { key ->
            findIdempotent(command.customerId, key, command.requestFingerprint!!)?.let { return it }
        }

        if (command.grossTotalMinor < 0 || command.plusDiscountMinor < 0 ||
            command.plusDiscountMinor > command.grossTotalMinor ||
            command.quotedTotalMinor != command.grossTotalMinor - command.plusDiscountMinor
        ) {
            throw BookingRuleViolation("Invalid booking price breakdown")
        }
        if ((command.plusPassId == null) != (command.plusDiscountMinor == 0L)) {
            throw BookingRuleViolation("A Plus pass and discount must be supplied together")
        }
        if (command.visits.any { it.quotedPriceMinor > 0 } &&
            command.visits.sumOf(VisitCommand::quotedPriceMinor) != command.quotedTotalMinor
        ) {
            throw BookingRuleViolation("Per-visit prices must equal the booking quote")
        }
        if (command.serviceAddressId != null &&
            listOf(
                command.serviceAddressLabel,
                command.serviceAddressLine,
                command.serviceAddressPincode,
                command.serviceLatitude,
                command.serviceLongitude,
            ).any { it == null }
        ) {
            throw BookingRuleViolation("A booking address snapshot must be complete")
        }

        val capacities = lockCapacities(command)
        validateInternalOverlaps(command)
        command.visits.forEach { visit ->
            bookingWindowPolicy.validate(command.bookingType, visit.startsAt, command.duration)
        }
        val unavailableVisits = command.visits.mapIndexedNotNull { index, visit ->
            runCatching { validateCapacityAndOverlap(visit, command.duration.toMinutes(), capacities, now) }
                .exceptionOrNull()
                ?.let { ConcurrentVisitFailure(index, visit.startsAt, it.message.orEmpty()) }
        }
        if (unavailableVisits.isNotEmpty()) throw AtomicSlotReservationFailed(unavailableVisits)

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
                grossTotalMinor = command.grossTotalMinor,
                plusDiscountMinor = command.plusDiscountMinor,
                plusPassId = command.plusPassId,
                serviceAddressId = command.serviceAddressId,
                serviceAddressLabel = command.serviceAddressLabel,
                serviceAddressLine = command.serviceAddressLine,
                serviceAddressPincode = command.serviceAddressPincode,
                idempotencyKey = command.idempotencyKey,
                requestFingerprint = command.requestFingerprint,
                expertPreference = command.expertPreference,
                favouriteWorkerId = command.favouriteWorkerId,
                fallbackPolicy = command.fallbackPolicy,
                maximumHoldExpiresAt = now.plus(properties.maximumTotalHoldTime),
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
                quotedPriceMinor = visit.quotedPriceMinor,
                assignmentType = visit.assignmentType,
                preferredWorkerId = visit.preferredWorkerId,
                        assignedWorkerTier = visit.assignedWorkerTier,
                requirementsSnapshot = command.requirements,
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
    fun findIdempotent(customerId: UUID, idempotencyKey: String, requestFingerprint: String): BookingSnapshot? {
        val booking = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey) ?: return null
        if (booking.requestFingerprint != requestFingerprint) {
            throw BookingRuleViolation("Idempotency key was already used with different booking details")
        }
        return currentSnapshot(booking)
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
            grossTotalMinor = booking.grossTotalMinor,
            plusDiscountMinor = booking.plusDiscountMinor,
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

    @Transactional(readOnly = true)
    fun plusPassContext(bookingId: UUID): PlusPassBookingContext {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        return PlusPassBookingContext(
            booking.id,
            booking.customerId,
            booking.status.name,
            booking.plusPassId,
            requireHolds(bookingId).minOf(SlotHold::expiresAt),
            booking.confirmedAt,
        )
    }

    @Transactional(readOnly = true)
    fun cancellationContext(bookingId: UUID): CancellationContext {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        val holds = requireHolds(bookingId)
        return CancellationContext(
            booking.id,
            booking.customerId,
            booking.status.name,
            booking.quotedTotalMinor,
            holds.minOf(SlotHold::startsAt),
            holds.all { it.status == SlotHoldStatus.CONFIRMED && it.jobStatus == JobStatus.ASSIGNED },
            booking.cancellationType,
        )
    }

    @Transactional
    fun cancelConfirmed(
        bookingId: UUID,
        expectedCustomerId: UUID,
        plusActive: Boolean,
        rawReason: String?,
        policy: BookingCancellationPolicy,
    ): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.customerId != expectedCustomerId) throw com.snabbit.prototype.booking.exception.CustomerAccessDenied()
        if (booking.status == BookingStatus.CANCELLED && booking.cancellationType == "CUSTOMER") {
            return currentSnapshot(booking)
        }
        if (booking.status != BookingStatus.CONFIRMED) {
            throw InvalidBookingState("Only a confirmed booking can be cancelled with a refund")
        }
        val holds = requireHolds(bookingId)
        if (holds.any { it.status != SlotHoldStatus.CONFIRMED || it.jobStatus != JobStatus.ASSIGNED }) {
            throw InvalidBookingState("A booking cannot be cancelled after worker arrival or service activity")
        }
        val earliestStart = holds.minOf(SlotHold::startsAt)
        if (!earliestStart.isAfter(now)) throw InvalidBookingState("A booking cannot be cancelled after its start time")
        val quote = policy.quote(booking.id, booking.quotedTotalMinor, earliestStart, plusActive, now)
        val reason = rawReason?.trim()?.takeIf(String::isNotBlank)?.also {
            if (it.length > 240) throw BookingRuleViolation("Cancellation reason cannot exceed 240 characters")
        }
        holds.forEach(SlotHold::cancelAssignment)
        booking.cancelConfirmed(reason, quote.cancellationFeeMinor, quote.refundAmountMinor, now)
        outboxWriter.append(
            booking.id,
            "BookingCustomerCancelled",
            mapOf(
                "bookingId" to booking.id,
                "customerId" to booking.customerId,
                "cancellationFeeMinor" to quote.cancellationFeeMinor,
                "refundAmountMinor" to quote.refundAmountMinor,
                "plusActive" to plusActive,
                "cancelledAt" to now,
            ),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun cancelConfirmedByPlatform(
        bookingId: UUID,
        rawReason: String,
        staffId: UUID? = null,
    ): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.status == BookingStatus.CANCELLED && booking.cancellationType == "PLATFORM") {
            return currentSnapshot(booking)
        }
        if (booking.status != BookingStatus.CONFIRMED) {
            throw InvalidBookingState("Only a confirmed booking can be cancelled by the platform")
        }
        val reason = rawReason.trim()
        if (reason.isBlank() || reason.length > 240) {
            throw BookingRuleViolation("Platform cancellation reason must contain 1 to 240 characters")
        }
        val holds = requireHolds(bookingId)
        if (holds.any {
                it.status != SlotHoldStatus.CONFIRMED ||
                    (it.jobStatus != JobStatus.ASSIGNED && it.jobStatus != JobStatus.ARRIVED)
            }
        ) {
            throw InvalidBookingState("Platform cannot cancel after service has started or completed")
        }
        holds.forEach(SlotHold::cancelByPlatform)
        booking.cancelByPlatform(reason, staffId, now)
        val eventPayload = mutableMapOf<String, Any>(
            "bookingId" to booking.id,
            "customerId" to booking.customerId,
            "refundAmountMinor" to booking.quotedTotalMinor,
            "cancellationFeeMinor" to 0,
            "cancelledAt" to now,
        )
        staffId?.let { eventPayload["cancelledByStaffId"] = it }
        outboxWriter.append(
            booking.id,
            "BookingPlatformCancelled",
            eventPayload,
            now,
        )
        return currentSnapshot(booking)
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
        holds.forEach { hold ->
            outboxWriter.append(
                bookingId,
                "VisitAssigned",
                mapOf(
                    "bookingId" to bookingId,
                    "customerId" to booking.customerId,
                    "visitIndex" to hold.visitIndex,
                    "workerId" to hold.workerId,
                    "startsAt" to hold.startsAt,
                    "assignmentType" to hold.assignmentType.name,
                ),
                now,
            )
        }
        return currentSnapshot(booking)
    }

    @Transactional
    fun extendHoldsForPayment(bookingId: UUID): PaymentQuoteSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.status != BookingStatus.AWAITING_PAYMENT) {
            throw InvalidBookingState("Booking is not awaiting payment")
        }
        val holds = requireHolds(bookingId)
        if (holds.any { !it.isActive(now) }) throw InvalidBookingState("Booking slot hold has expired")
        val maximum = booking.maximumHoldExpiresAt ?: booking.createdAt.plus(properties.maximumTotalHoldTime)
        val extended = minOf(now.plus(properties.paymentHoldExtension), maximum)
        if (!extended.isAfter(now)) throw InvalidBookingState("Booking maximum hold time has expired")
        holds.forEach { it.extendHold(extended) }
        return paymentQuote(bookingId)
    }

    @Transactional
    fun cancel(bookingId: UUID, expectedCustomerId: UUID? = null): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (expectedCustomerId != null && booking.customerId != expectedCustomerId) {
            throw com.snabbit.prototype.booking.exception.CustomerAccessDenied()
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
    fun cancelVisit(bookingId: UUID, visitIndex: Int, customerId: UUID, reason: String?): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        if (booking.customerId != customerId) throw CustomerAccessDenied()
        if (booking.status != BookingStatus.CONFIRMED) {
            throw InvalidBookingState("Only a visit in a confirmed booking can be cancelled")
        }
        val hold = slotHoldRepository.findVisitForUpdate(bookingId, visitIndex)
            ?: throw InvalidBookingState("Booking visit does not exist")
        if (hold.refundStatus.name != "NONE") return currentSnapshot(booking)
        if (hold.jobStatus != JobStatus.ASSIGNED || !hold.startsAt.isAfter(now)) {
            throw InvalidBookingState("Only an unstarted future visit can be cancelled")
        }
        val refundKey = UUID.randomUUID()
        hold.markRefundPending(hold.quotedPriceMinor, refundKey, now)
        outboxWriter.append(
            bookingId,
            "VisitRefundRequested",
            mapOf(
                "bookingId" to bookingId,
                "customerId" to customerId,
                "visitIndex" to visitIndex,
                "amountMinor" to hold.quotedPriceMinor,
                "refundIdempotencyKey" to refundKey,
                "reason" to (reason ?: "Customer cancelled visit"),
            ),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun completeVisitRefund(refundIdempotencyKey: UUID) {
        val hold = slotHoldRepository.findByRefundIdempotencyKey(refundIdempotencyKey) ?: return
        hold.markRefundCompleted()
        val now = clock.instant()
        outboxWriter.append(
            hold.bookingId,
            "VisitRefunded",
            mapOf(
                "bookingId" to hold.bookingId,
                "customerId" to hold.customerId,
                "visitIndex" to hold.visitIndex,
                "amountMinor" to (hold.refundAmountMinor ?: 0),
                "refundIdempotencyKey" to refundIdempotencyKey,
            ),
            now,
        )
    }

    @Transactional(readOnly = true)
    fun recoveryContext(bookingId: UUID, visitIndex: Int, failedWorkerId: UUID): VisitRecoveryContext {
        val booking = bookingRepository.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        if (booking.status != BookingStatus.CONFIRMED) throw InvalidBookingState("Booking is not confirmed")
        val hold = slotHoldRepository.findAllByBookingIdOrderByVisitIndex(bookingId)
            .firstOrNull { it.visitIndex == visitIndex } ?: throw InvalidBookingState("Booking visit does not exist")
        if (hold.workerId != failedWorkerId || hold.jobStatus != JobStatus.ASSIGNED) {
            throw InvalidBookingState("Visit is not assigned to the reported worker")
        }
        return VisitRecoveryContext(
            booking.id, booking.customerId, booking.serviceId, visitIndex, failedWorkerId,
            hold.startsAt, booking.durationMinutes, hold.quotedPriceMinor, hold.assignedWorkerTier,
            booking.serviceLatitude ?: throw InvalidBookingState("Booking has no service latitude"),
            booking.serviceLongitude ?: throw InvalidBookingState("Booking has no service longitude"),
            hold.requirementsSnapshot, booking.expertPreference, booking.favouriteWorkerId, booking.fallbackPolicy,
        )
    }

    @Transactional
    fun reassignVisit(
        bookingId: UUID,
        visitIndex: Int,
        failedWorkerId: UUID,
        replacementWorkerId: UUID,
        replacementWorkerTier: String,
        assignmentType: AssignmentType,
    ): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val hold = slotHoldRepository.findVisitForUpdate(bookingId, visitIndex)
            ?: throw InvalidBookingState("Booking visit does not exist")
        if (booking.status != BookingStatus.CONFIRMED || hold.workerId != failedWorkerId || hold.jobStatus != JobStatus.ASSIGNED) {
            throw InvalidBookingState("Visit can no longer be reassigned")
        }
        val date = hold.startsAt.atZone(properties.zoneId).toLocalDate()
        val capacity = capacityRepository.findForUpdate(replacementWorkerId, date)
            ?: throw SlotUnavailable("Replacement worker has no availability on $date")
        val localStart = hold.startsAt.atZone(properties.zoneId)
        val localEnd = hold.endsAt.atZone(properties.zoneId)
        if (localStart.toLocalTime() < capacity.availableFrom || localEnd.toLocalTime() > capacity.availableUntil ||
            slotHoldRepository.hasActiveOverlap(replacementWorkerId, hold.startsAt, hold.endsAt, now)
        ) throw SlotUnavailable("Replacement worker slot is no longer available")
        hold.workerId = replacementWorkerId
        hold.assignedWorkerTier = replacementWorkerTier
        hold.assignmentType = assignmentType
        outboxWriter.append(
            bookingId,
            "VisitAssignmentChanged",
            mapOf(
                "bookingId" to bookingId,
                "customerId" to booking.customerId,
                "visitIndex" to visitIndex,
                "previousWorkerId" to failedWorkerId,
                "workerId" to replacementWorkerId,
                "quotedPriceMinor" to hold.quotedPriceMinor,
            ),
            now,
        )
        return currentSnapshot(booking)
    }

    @Transactional
    fun refundVisitForDropout(bookingId: UUID, visitIndex: Int, failedWorkerId: UUID): BookingSnapshot {
        val now = clock.instant()
        val booking = requireBookingForUpdate(bookingId)
        val hold = slotHoldRepository.findVisitForUpdate(bookingId, visitIndex)
            ?: throw InvalidBookingState("Booking visit does not exist")
        if (booking.status != BookingStatus.CONFIRMED || hold.workerId != failedWorkerId || hold.jobStatus != JobStatus.ASSIGNED) {
            throw InvalidBookingState("Visit can no longer be refunded")
        }
        val key = UUID.randomUUID()
        hold.markRefundPending(hold.quotedPriceMinor, key, now)
        outboxWriter.append(
            bookingId,
            "VisitRefundRequested",
            mapOf(
                "bookingId" to bookingId,
                "customerId" to booking.customerId,
                "visitIndex" to visitIndex,
                "amountMinor" to hold.quotedPriceMinor,
                "refundIdempotencyKey" to key,
                "reason" to "Worker unavailable",
            ),
            now,
        )
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
            BookingType.MULTI_DAY -> if (count !in properties.minMultiDayVisits..properties.maxMultiDayVisits) {
                throw BookingRuleViolation(
                    "Multi-day bookings require ${properties.minMultiDayVisits} to ${properties.maxMultiDayVisits} visits",
                )
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
            grossTotalMinor = booking.grossTotalMinor,
            plusDiscountMinor = booking.plusDiscountMinor,
            plusPassId = booking.plusPassId,
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
                    date = hold.startsAt.atZone(properties.zoneId).toLocalDate(),
                    startTime = hold.startsAt.atZone(properties.zoneId).toLocalTime(),
                    durationMinutes = booking.durationMinutes,
                    quotedPriceMinor = hold.quotedPriceMinor,
                    assignmentType = hold.assignmentType.name,
                    refundStatus = hold.refundStatus.name,
                    refundAmountMinor = hold.refundAmountMinor,
                    refundIdempotencyKey = hold.refundIdempotencyKey,
                    cancelledAt = hold.cancelledAt,
                )
            },
            cancellationType = booking.cancellationType,
            cancellationReason = booking.cancellationReason,
            cancellationFeeMinor = booking.cancellationFeeMinor,
            refundAmountMinor = booking.refundAmountMinor,
            cancelledAt = booking.cancelledAt,
        )
    }
}
