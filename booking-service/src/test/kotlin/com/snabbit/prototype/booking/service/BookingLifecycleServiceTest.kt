package com.snabbit.prototype.booking.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingRequirements
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
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.exception.SlotUnavailable
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.OutboxEventRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BookingLifecycleServiceTest {
    private val bookingRepository = mockk<BookingRepository>()
    private val capacityRepository = mockk<WorkerDayCapacityRepository>()
    private val holdRepository = mockk<SlotHoldRepository>()
    private val offerRepository = mockk<WorkerOfferRepository>()
    private val outboxRepository = mockk<OutboxEventRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-02T04:00:00Z"), ZoneOffset.UTC)
    private val properties = BookingProperties(
        ZoneId.of("Asia/Kolkata"),
        LocalTime.of(8, 0),
        LocalTime.of(19, 0),
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofSeconds(30),
        100,
    )
    private val service = BookingLifecycleService(
        bookingRepository,
        capacityRepository,
        holdRepository,
        offerRepository,
        BookingWindowPolicy(properties, clock),
        OutboxWriter(outboxRepository, ObjectMapper().findAndRegisterModules()),
        properties,
        clock,
    )

    @Test
    fun `creates every visit and offer in a multi-day booking`() {
        val workerId = UUID.randomUUID()
        val command = multiDayCommand(workerId)
        command.visits.forEach { visit ->
            val date = visit.startsAt.atZone(properties.zoneId).toLocalDate()
            every { capacityRepository.findForUpdate(workerId, date) } returns capacity(workerId, date)
            every { holdRepository.hasActiveOverlap(workerId, visit.startsAt, any(), clock.instant()) } returns false
        }
        every { bookingRepository.saveAndFlush(any()) } answers { firstArg() }
        every { holdRepository.saveAll<SlotHold>(any()) } answers { firstArg() }
        every { offerRepository.saveAll<WorkerOffer>(any()) } answers { firstArg() }
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.create(command)

        assertEquals(BookingStatus.AWAITING_WORKER.name, result.status)
        assertEquals(2, result.visits.size)
        assertEquals(command.quotedTotalMinor, result.quotedTotalMinor)
        assertEquals(command.requirements, result.requirements)
        assertEquals(result.visits[0].holdExpiresAt, result.visits[1].holdExpiresAt)
        verify(exactly = 1) { bookingRepository.saveAndFlush(any()) }
    }

    @Test
    fun `does not create a partial booking when one day has no capacity`() {
        val workerId = UUID.randomUUID()
        val command = multiDayCommand(workerId)
        val firstDate = command.visits.first().startsAt.atZone(properties.zoneId).toLocalDate()
        val secondDate = command.visits.last().startsAt.atZone(properties.zoneId).toLocalDate()
        every { capacityRepository.findForUpdate(workerId, firstDate) } returns capacity(workerId, firstDate)
        every { capacityRepository.findForUpdate(workerId, secondDate) } returns null

        assertThrows(SlotUnavailable::class.java) { service.create(command) }

        verify(exactly = 0) { bookingRepository.saveAndFlush(any()) }
        verify(exactly = 0) { holdRepository.saveAll<SlotHold>(any()) }
    }

    @Test
    fun `idempotency key replays only the original booking request`() {
        val customerId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val booking = booking(
            bookingId,
            customerId = customerId,
            idempotencyKey = "booking-request-001",
            requestFingerprint = "a".repeat(64),
        )
        val hold = hold(bookingId, workerId)
        val offer = offer(bookingId, workerId, hold.id)
        every {
            bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, "booking-request-001")
        } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId) } returns listOf(offer)

        val replay = service.findIdempotent(customerId, "booking-request-001", "a".repeat(64))

        assertEquals(bookingId, replay?.bookingId)
        assertThrows(BookingRuleViolation::class.java) {
            service.findIdempotent(customerId, "booking-request-001", "b".repeat(64))
        }
    }

    @Test
    fun `last worker acceptance moves booking to payment`() {
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val hold = hold(bookingId, workerId)
        val offer = offer(bookingId, workerId, hold.id)
        val booking = booking(bookingId)
        every { bookingRepository.findForUpdate(bookingId) } returns booking
        every { offerRepository.findForUpdate(bookingId, offer.id) } returns offer
        every { offerRepository.countByBookingIdAndStatusNot(bookingId, WorkerOfferStatus.ACCEPTED) } returns 0
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.acceptOffer(bookingId, offer.id, workerId)
        val retried = service.acceptOffer(bookingId, offer.id, workerId)

        assertEquals(BookingStatus.AWAITING_PAYMENT.name, result.status)
        assertEquals(WorkerOfferStatus.ACCEPTED.name, result.visits.single().offerStatus)
        assertEquals(BookingStatus.AWAITING_PAYMENT.name, retried.status)
        verify(exactly = 2) { outboxRepository.save(any()) }
    }

    @Test
    fun `verified payment confirms the booking and every hold`() {
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val booking = booking(bookingId).apply { awaitPayment(clock.instant()) }
        val hold = hold(bookingId, workerId)
        val offer = offer(bookingId, workerId, hold.id).apply { accept(clock.instant()) }
        every { bookingRepository.findForUpdate(bookingId) } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.confirmPayment(bookingId, "pay_verified_001")

        assertEquals(BookingStatus.CONFIRMED.name, result.status)
        assertEquals(SlotHoldStatus.CONFIRMED, hold.status)
        assertEquals("pay_verified_001", booking.paymentReference)
        assertEquals(clock.instant(), booking.confirmedAt)
    }

    @Test
    fun `payment arriving after hold expiry cannot confirm capacity`() {
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val booking = booking(bookingId).apply { awaitPayment(clock.instant()) }
        val expiredHold = hold(bookingId, workerId).also { it.expire() }
        val offer = offer(bookingId, workerId, expiredHold.id).apply { accept(clock.instant()) }
        every { bookingRepository.findForUpdate(bookingId) } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(expiredHold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(bookingId) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.confirmPayment(bookingId, "pay_too_late")

        assertEquals(BookingStatus.EXPIRED.name, result.status)
        assertEquals(null, booking.paymentReference)
    }

    @Test
    fun `confirmed customer cancellation releases unstarted visit and records Plus fee`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_verified", clock.instant())
        }
        val hold = hold(booking.id, UUID.randomUUID()).apply { confirm() }
        val offer = offer(booking.id, hold.workerId, hold.id).apply { accept(clock.instant()) }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }
        val policy = BookingCancellationPolicy(
            BookingCancellationProperties(Duration.ofHours(2), 5_000, 10_000, 50),
        )

        val result = service.cancelConfirmed(booking.id, booking.customerId, true, "Plans changed", policy)

        assertEquals(BookingStatus.CANCELLED.name, result.status)
        assertEquals("CUSTOMER", result.cancellationType)
        assertEquals(5_000, result.cancellationFeeMinor)
        assertEquals(37_000, result.refundAmountMinor)
        assertEquals(SlotHoldStatus.RELEASED, hold.status)
        assertEquals(null, hold.jobStatus)
        verify(exactly = 1) { outboxRepository.save(match { it.eventType == "BookingCustomerCancelled" }) }
    }

    @Test
    fun `customer cannot cancel after worker arrival`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_verified", clock.instant())
        }
        val hold = hold(booking.id, UUID.randomUUID()).apply {
            confirm()
            arrive(BigDecimal("19.076090"), BigDecimal("72.877426"), BigDecimal.TEN, clock.instant())
        }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)

        assertThrows(InvalidBookingState::class.java) {
            service.cancelConfirmed(
                booking.id,
                booking.customerId,
                false,
                null,
                BookingCancellationPolicy(
                    BookingCancellationProperties(Duration.ofHours(2), 5_000, 10_000, 50),
                ),
            )
        }

        assertEquals(BookingStatus.CONFIRMED, booking.status)
        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `platform cancellation after arrival grants full refund before service start`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_verified", clock.instant())
        }
        val hold = hold(booking.id, UUID.randomUUID()).apply {
            confirm()
            arrive(BigDecimal("19.076090"), BigDecimal("72.877426"), BigDecimal.TEN, clock.instant())
        }
        val offer = offer(booking.id, hold.workerId, hold.id).apply { accept(clock.instant()) }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }
        val staffId = UUID.randomUUID()

        val result = service.cancelConfirmedByPlatform(booking.id, "Replacement worker unavailable", staffId)

        assertEquals("PLATFORM", result.cancellationType)
        assertEquals(0, result.cancellationFeeMinor)
        assertEquals(booking.quotedTotalMinor, result.refundAmountMinor)
        assertEquals(staffId, booking.cancelledByStaffId)
        assertEquals(SlotHoldStatus.RELEASED, hold.status)
        verify(exactly = 1) { outboxRepository.save(match { it.eventType == "BookingPlatformCancelled" }) }
    }

    @Test
    fun `payment quote exposes authoritative customer amount state and expiry`() {
        val bookingId = UUID.randomUUID()
        val workerId = UUID.randomUUID()
        val booking = booking(bookingId).apply { awaitPayment(clock.instant()) }
        val hold = hold(bookingId, workerId)
        every { bookingRepository.findById(bookingId) } returns Optional.of(booking)
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(bookingId) } returns listOf(hold)

        val quote = service.paymentQuote(bookingId)

        assertEquals(booking.customerId, quote.customerId)
        assertEquals(booking.quotedTotalMinor, quote.quotedTotalMinor)
        assertEquals(BookingStatus.AWAITING_PAYMENT.name, quote.status)
        assertEquals(hold.expiresAt, quote.holdExpiresAt)
        assertEquals("INR", quote.currency)
    }

    @Test
    fun `customer cannot cancel another customers hold`() {
        val booking = booking(UUID.randomUUID())
        every { bookingRepository.findForUpdate(booking.id) } returns booking

        assertThrows(CustomerAccessDenied::class.java) {
            service.cancel(booking.id, UUID.randomUUID())
        }

        verify(exactly = 0) { holdRepository.findAllByBookingIdOrderByVisitIndex(any()) }
    }

    @Test
    fun `assigned worker arrives starts and completes confirmed visit`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_worker_flow", clock.instant())
        }
        val workerId = UUID.randomUUID()
        val hold = hold(booking.id, workerId).apply { confirm() }
        val offer = offer(booking.id, workerId, hold.id).apply { accept(clock.instant()) }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findVisitForUpdate(booking.id, 0) } returns hold
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)
        every { offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id) } returns listOf(offer)
        every { outboxRepository.save(any()) } answers { firstArg() }

        service.workerArrive(
            booking.id,
            0,
            workerId,
            WorkerLocationCommand(BigDecimal("19.076090"), BigDecimal("72.877426"), BigDecimal("20")),
        )
        service.workerStart(booking.id, 0, workerId)
        val completed = service.workerComplete(booking.id, 0, workerId)

        assertEquals(JobStatus.COMPLETED, hold.jobStatus)
        assertEquals(BookingStatus.COMPLETED.name, completed.status)
        assertEquals(JobStatus.COMPLETED.name, completed.visits.single().jobStatus)
        assertEquals(clock.instant(), hold.arrivedAt)
        assertEquals(clock.instant(), hold.startedAt)
        assertEquals(clock.instant(), hold.completedAt)
        verify(exactly = 4) { outboxRepository.save(any()) }
    }

    @Test
    fun `arrival requires accurate location near service address`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_bad_location", clock.instant())
        }
        val workerId = UUID.randomUUID()
        val hold = hold(booking.id, workerId).apply { confirm() }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findVisitForUpdate(booking.id, 0) } returns hold

        assertThrows(com.snabbit.prototype.booking.exception.BookingRuleViolation::class.java) {
            service.workerArrive(
                booking.id,
                0,
                workerId,
                WorkerLocationCommand(BigDecimal("19.100000"), BigDecimal("72.900000"), BigDecimal("10")),
            )
        }

        assertEquals(JobStatus.ASSIGNED, hold.jobStatus)
        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `arrival rejects a point beyond the configured two hundred metre boundary`() {
        val booking = booking(UUID.randomUUID()).apply {
            awaitPayment(clock.instant())
            confirm("pay_outside_geofence", clock.instant())
        }
        val workerId = UUID.randomUUID()
        val hold = hold(booking.id, workerId).apply { confirm() }
        every { bookingRepository.findForUpdate(booking.id) } returns booking
        every { holdRepository.findVisitForUpdate(booking.id, 0) } returns hold

        assertEquals(200.0, properties.arrivalRadiusMeters)
        assertThrows(com.snabbit.prototype.booking.exception.BookingRuleViolation::class.java) {
            service.workerArrive(
                booking.id,
                0,
                workerId,
                WorkerLocationCommand(BigDecimal("19.076090"), BigDecimal("72.879426"), BigDecimal("10")),
            )
        }

        assertEquals(JobStatus.ASSIGNED, hold.jobStatus)
        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `rating context exposes only authoritative completed visits`() {
        val booking = booking(UUID.randomUUID())
        val workerId = UUID.randomUUID()
        val hold = hold(booking.id, workerId).apply {
            confirm()
            arrive(BigDecimal("19.076090"), BigDecimal("72.877426"), BigDecimal.TEN, clock.instant())
            start(clock.instant())
            complete(clock.instant())
        }
        every { bookingRepository.findById(booking.id) } returns Optional.of(booking)
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)

        val context = service.ratingContext(booking.id)

        assertEquals(booking.customerId, context.customerId)
        assertEquals(workerId, context.visits.single().workerId)
        assertEquals(true, context.visits.single().completed)
    }

    @Test
    fun `ownership context exposes the authoritative booking customer`() {
        val booking = booking(UUID.randomUUID())
        every { bookingRepository.findById(booking.id) } returns Optional.of(booking)

        val ownership = service.ownership(booking.id)

        assertEquals(booking.id, ownership.bookingId)
        assertEquals(booking.customerId, ownership.customerId)
        verify(exactly = 0) { holdRepository.findAllByBookingIdOrderByVisitIndex(any()) }
    }

    @Test
    fun `Plus pass context exposes durable pass and confirmation state`() {
        val passId = UUID.randomUUID()
        val booking = booking(UUID.randomUUID(), passId).apply {
            awaitPayment(clock.instant())
            confirm("pay_verified", clock.instant())
        }
        val hold = hold(booking.id, UUID.randomUUID()).apply { confirm() }
        every { bookingRepository.findById(booking.id) } returns Optional.of(booking)
        every { holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id) } returns listOf(hold)

        val context = service.plusPassContext(booking.id)

        assertEquals(passId, context.plusPassId)
        assertEquals(BookingStatus.CONFIRMED.name, context.status)
        assertEquals(clock.instant(), context.confirmedAt)
        assertEquals(hold.expiresAt, context.holdExpiresAt)
    }

    private fun multiDayCommand(workerId: UUID) = CreateBookingCommand(
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.MULTI_DAY,
        duration = Duration.ofHours(2),
        visits = listOf(
            VisitCommand(workerId, Instant.parse("2026-10-03T04:30:00Z")),
            VisitCommand(workerId, Instant.parse("2026-10-04T04:30:00Z")),
        ),
        requirements = BookingRequirements(
            hasPets = true,
            preferredLanguages = listOf("HINDI"),
            allergies = listOf("DUST"),
            workerConstraints = listOf("MASK_REQUIRED"),
        ),
        quotedTotalMinor = 75_000,
    )

    private fun capacity(workerId: UUID, date: LocalDate) = WorkerDayCapacity(
        id = UUID.randomUUID(),
        workerId = workerId,
        serviceDate = date,
        availableFrom = LocalTime.of(8, 0),
        availableUntil = LocalTime.of(19, 0),
    )

    private fun booking(
        id: UUID,
        plusPassId: UUID? = null,
        customerId: UUID = UUID.randomUUID(),
        idempotencyKey: String? = null,
        requestFingerprint: String? = null,
    ) = Booking(
        id = id,
        customerId = customerId,
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.SCHEDULED,
        status = BookingStatus.AWAITING_WORKER,
        durationMinutes = 120,
        quotedTotalMinor = 42_000,
        serviceLatitude = BigDecimal("19.076090"),
        serviceLongitude = BigDecimal("72.877426"),
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
        grossTotalMinor = if (plusPassId == null) 42_000 else 45_000,
        plusDiscountMinor = if (plusPassId == null) 0 else 3_000,
        plusPassId = plusPassId,
        idempotencyKey = idempotencyKey,
        requestFingerprint = requestFingerprint,
    )

    private fun hold(bookingId: UUID, workerId: UUID) = SlotHold(
        id = UUID.randomUUID(),
        bookingId = bookingId,
        visitIndex = 0,
        customerId = UUID.randomUUID(),
        workerId = workerId,
        serviceId = UUID.randomUUID(),
        startsAt = Instant.parse("2026-10-02T04:30:00Z"),
        endsAt = Instant.parse("2026-10-02T06:30:00Z"),
        status = SlotHoldStatus.HELD,
        expiresAt = clock.instant().plusSeconds(300),
        createdAt = clock.instant(),
    )

    private fun offer(bookingId: UUID, workerId: UUID, holdId: UUID) = WorkerOffer(
        id = UUID.randomUUID(),
        bookingId = bookingId,
        slotHoldId = holdId,
        workerId = workerId,
        status = WorkerOfferStatus.OFFERED,
        expiresAt = clock.instant().plusSeconds(60),
        createdAt = clock.instant(),
    )
}
