package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.request.ActivatePlusCommand

import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class PlusPersistenceIntegrationTest {
    @Autowired private lateinit var plus: PlusService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val userId = UUID.fromString("40000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun cleanEntitlements() {
        jdbc.execute("TRUNCATE plus_pass, plus_subscription, processed_message CASCADE")
    }

    @Test
    fun `subscription grants a pass that can be durably reserved and consumed`() {
        val now = Instant.now()
        val status = plus.activate(
            userId,
            ActivatePlusCommand("verified-payment-1", now.minusSeconds(1), now.plusSeconds(86_400), 1, 3_000, "HOUSE_CLEANING"),
        )
        assertEquals(1, status.availablePasses)

        val bookingId = UUID.randomUUID()
        val reservation = plus.reservePass(
            userId, bookingId, "HOUSE_CLEANING", 18_000, now.plusSeconds(300),
        )
        assertEquals(15_000, reservation.netTotalMinor)

        plus.consumePass(bookingId, now.plusSeconds(120))

        assertEquals("USED", jdbc.queryForObject("SELECT status FROM plus_pass WHERE id = ?", String::class.java, reservation.passId))
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("users")
            .withUsername("snabbit")
            .withPassword("snabbit")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
