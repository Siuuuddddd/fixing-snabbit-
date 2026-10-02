package com.snabbit.prototype.booking.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.BookingStatus
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.SlotHoldStatus
import com.snabbit.prototype.booking.domain.WorkerDayCapacity
import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.domain.WorkerOfferStatus
import com.snabbit.prototype.booking.domain.JobStatus
import com.snabbit.prototype.booking.persistence.BookingRepository
import com.snabbit.prototype.booking.persistence.OutboxEventRepository
import com.snabbit.prototype.booking.persistence.SlotHoldRepository
import com.snabbit.prototype.booking.persistence.WorkerDayCapacityRepository
import com.snabbit.prototype.booking.persistence.WorkerOfferRepository
import com.snabbit.prototype.booking.support.SlotUnavailable
import com.snabbit.prototype.booking.support.CustomerAccessDenied
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.Optional
import java.math.BigDecimal

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

        assertThrows(com.snabbit.prototype.booking.support.BookingRuleViolation::class.java) {
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

    private fun booking(id: UUID) = Booking(
        id = id,
        customerId = UUID.randomUUID(),
        serviceId = UUID.randomUUID(),
        bookingType = BookingType.SCHEDULED,
        status = BookingStatus.AWAITING_WORKER,
        durationMinutes = 120,
        quotedTotalMinor = 42_000,
        serviceLatitude = BigDecimal("19.076090"),
        serviceLongitude = BigDecimal("72.877426"),
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
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
