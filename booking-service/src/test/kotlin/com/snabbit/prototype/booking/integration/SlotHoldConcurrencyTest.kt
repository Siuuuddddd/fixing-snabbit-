package com.snabbit.prototype.booking.integration

import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.dto.request.CreateBookingCommand
import com.snabbit.prototype.booking.dto.request.VisitCommand
import com.snabbit.prototype.booking.events.OutboxDeliveryStore
import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.SlotUnavailable
import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerMatchResponse
import com.snabbit.prototype.booking.matching.MatchingClients
import com.snabbit.prototype.booking.service.AssignmentRecoveryService
import com.snabbit.prototype.booking.service.BookingLifecycleService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "booking.expiry-scan-delay=PT24H",
        "eventing.outbox-enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "booking.advance-window-days=11",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class SlotHoldConcurrencyTest {
    @Autowired
    private lateinit var service: BookingLifecycleService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var outboxStore: OutboxDeliveryStore

    private val workerId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    private val serviceId = UUID.fromString("30000000-0000-0000-0000-000000000001")
    private lateinit var startsAt: ZonedDateTime

    @BeforeEach
    fun prepareCapacity() {
        jdbc.execute("TRUNCATE worker_offer, slot_hold, booking, worker_day_capacity, outbox_event CASCADE")
        startsAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
            .plusDays(1)
            .with(LocalTime.of(10, 0))
        jdbc.update(
            """
            INSERT INTO worker_day_capacity
                (id, worker_id, service_date, available_from, available_until, version)
            VALUES (?, ?, ?, '08:00', '19:00', 0)
            """.trimIndent(),
            UUID.randomUUID(),
            workerId,
            startsAt.toLocalDate(),
        )
    }

    @Test
    fun `only one concurrent request can hold the same worker slot`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val tasks = (1..2).map {
            Callable {
                ready.countDown()
                start.await()
                runCatching { service.create(command(UUID.randomUUID())) }
            }
        }

        val futures = tasks.map(executor::submit)
        ready.await()
        start.countDown()
        val results = futures.map { it.get() }
        executor.shutdown()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is SlotUnavailable })
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM booking", Long::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM slot_hold", Long::class.java))
    }

    @Test
    fun `multi-day failure rolls back every visit`() {
        val secondVisit = startsAt.plusDays(1)
        val command = CreateBookingCommand(
            customerId = UUID.randomUUID(),
            serviceId = serviceId,
            bookingType = BookingType.MULTI_DAY,
            duration = Duration.ofHours(2),
            visits = listOf(
                VisitCommand(workerId, startsAt.toInstant()),
                VisitCommand(workerId, secondVisit.toInstant()),
            ),
        )

        val result = runCatching { service.create(command) }

        assertTrue(result.exceptionOrNull() is SlotUnavailable)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM booking", Long::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM slot_hold", Long::class.java))
    }

    @Test
    fun `ten day plan persists every independently priced visit atomically`() {
        val customerId = UUID.randomUUID()
        val visits = (0 until 10).map { index ->
            val start = startsAt.plusDays(index.toLong())
            if (index > 0) {
                jdbc.update(
                    "INSERT INTO worker_day_capacity (id, worker_id, service_date, available_from, available_until, version) VALUES (?, ?, ?, '08:00', '19:00', 0)",
                    UUID.randomUUID(), workerId, start.toLocalDate(),
                )
            }
            VisitCommand(workerId, start.toInstant(), quotedPriceMinor = 1_000)
        }

        val result = service.create(
            CreateBookingCommand(
                customerId = customerId,
                serviceId = serviceId,
                bookingType = BookingType.MULTI_DAY,
                duration = Duration.ofHours(2),
                visits = visits,
                quotedTotalMinor = 10_000,
                grossTotalMinor = 10_000,
            ),
        )

        assertEquals(10, result.visits.size)
        assertEquals(10_000, result.visits.sumOf { it.quotedPriceMinor })
        assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM slot_hold", Int::class.java))
    }

    @Test
    fun `same customer idempotency key replays one booking and rejects changed details`() {
        val customerId = UUID.randomUUID()
        val original = command(customerId).copy(
            idempotencyKey = "booking-request-integration-001",
            requestFingerprint = "a".repeat(64),
        )

        val first = service.create(original)
        val replay = service.create(original.copy(bookingId = UUID.randomUUID()))
        val mismatch = runCatching {
            service.create(
                original.copy(
                    bookingId = UUID.randomUUID(),
                    requestFingerprint = "b".repeat(64),
                ),
            )
        }

        assertEquals(first.bookingId, replay.bookingId)
        assertTrue(mismatch.exceptionOrNull() is BookingRuleViolation)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM booking", Long::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM slot_hold", Long::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM worker_offer", Long::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long::class.java))
    }

    @Test
    fun `payment hold extension is bounded and payment failure releases the held slot`() {
        val customerId = UUID.randomUUID()
        val created = service.create(command(customerId))
        val originalExpiry = created.holdExpiresAt!!
        service.acceptOffer(created.bookingId, created.visits.single().offerId, workerId)

        val extended = service.extendHoldsForPayment(created.bookingId)
        val cancelled = service.cancel(created.bookingId, customerId)

        assertTrue(extended.holdExpiresAt.isAfter(originalExpiry))
        assertEquals("CANCELLED", cancelled.status)
        assertEquals(
            "RELEASED",
            jdbc.queryForObject(
                "SELECT status::text FROM slot_hold WHERE booking_id = ?",
                String::class.java,
                created.bookingId,
            ),
        )
    }

    @Test
    fun `expired payment hold releases every visit without confirmation`() {
        val customerId = UUID.randomUUID()
        val created = service.create(command(customerId))
        service.acceptOffer(created.bookingId, created.visits.single().offerId, workerId)
        jdbc.update(
            "UPDATE slot_hold SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE booking_id = ?",
            created.bookingId,
        )

        val expired = service.confirmPayment(created.bookingId, "wallet:late-payment")

        assertEquals("EXPIRED", expired.status)
        assertEquals(
            "EXPIRED",
            jdbc.queryForObject(
                "SELECT status::text FROM slot_hold WHERE booking_id = ?",
                String::class.java,
                created.bookingId,
            ),
        )
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'BookingExpired'",
                Int::class.java,
                created.bookingId.toString(),
            ),
        )
    }

    @Test
    fun `worker dropout reassigns the confirmed visit to a same-tier replacement`() = runBlocking {
        val replacementId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO worker_day_capacity (id, worker_id, service_date, available_from, available_until, version) VALUES (?, ?, ?, '08:00', '19:00', 0)",
            UUID.randomUUID(), replacementId, startsAt.toLocalDate(),
        )
        val created = service.create(
            command(UUID.randomUUID()).copy(
                serviceLatitude = BigDecimal("19.076090"),
                serviceLongitude = BigDecimal("72.877426"),
            ),
        )
        service.acceptOffer(created.bookingId, created.visits.single().offerId, workerId)
        service.confirmPayment(created.bookingId, "wallet:dropout-test")
        val catalog = mockk<ServiceCatalogService>()
        val clients = mockk<MatchingClients>()
        every { catalog.requireActive(serviceId) } returns ServiceOfferingSnapshot(
            serviceId, "HOUSE_CLEANING", "House Cleaning", 18_000,
        )
        coEvery { clients.matches(any()) } returns listOf(
            WorkerMatchResponse(
                replacementId, "Replacement", "STANDARD", BigDecimal("4.8"),
                BigDecimal.ONE, false, 36_000,
            ),
        )

        val recovered = AssignmentRecoveryService(service, catalog, clients)
            .recover(created.bookingId, 0, workerId)

        assertEquals(replacementId, recovered.visits.single().workerId)
        assertEquals("FALLBACK_SAME_TIER", recovered.visits.single().assignmentType)
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'VisitAssignmentChanged'",
                Int::class.java,
                created.bookingId.toString(),
            ),
        )
    }

    @Test
    fun `outbox lease prevents concurrent publishers and becomes retryable`() {
        val eventId = UUID.randomUUID()
        val now = Instant.now()
        jdbc.update(
            """
            INSERT INTO outbox_event
                (id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
            VALUES (?, 'Booking', ?, 'BookingHeld', '{}'::jsonb, ?)
            """.trimIndent(),
            eventId,
            UUID.randomUUID().toString(),
            Timestamp.from(now),
        )
        val firstClaim = UUID.randomUUID()
        val secondClaim = UUID.randomUUID()

        assertEquals(1, outboxStore.claimBatch(firstClaim, now, now.plusSeconds(30), 10).size)
        assertEquals(0, outboxStore.claimBatch(secondClaim, now, now.plusSeconds(30), 10).size)

        outboxStore.releaseAfterFailure(eventId, firstClaim, now.plusSeconds(5), "temporary")
        assertEquals(0, outboxStore.claimBatch(secondClaim, now.plusSeconds(4), now.plusSeconds(34), 10).size)
        assertEquals(1, outboxStore.claimBatch(secondClaim, now.plusSeconds(6), now.plusSeconds(36), 10).size)
    }

    private fun command(customerId: UUID) = CreateBookingCommand(
        customerId = customerId,
        serviceId = serviceId,
        bookingType = BookingType.SCHEDULED,
        duration = Duration.ofHours(2),
        visits = listOf(VisitCommand(workerId, startsAt.toInstant())),
    )

    class KPostgreSQLContainer(imageName: String) :
        PostgreSQLContainer<KPostgreSQLContainer>(imageName)

    companion object {
        @Container
        @JvmStatic
        val postgres = KPostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
