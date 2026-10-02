package com.snabbit.prototype.booking.integration

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.CreateBookingCommand
import com.snabbit.prototype.booking.service.VisitCommand
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.events.OutboxDeliveryStore
import com.snabbit.prototype.booking.support.SlotUnavailable
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
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest(
    properties = [
        "booking.expiry-scan-delay=PT24H",
        "eventing.outbox-enabled=false",
        "spring.kafka.listener.auto-startup=false",
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
            now,
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
