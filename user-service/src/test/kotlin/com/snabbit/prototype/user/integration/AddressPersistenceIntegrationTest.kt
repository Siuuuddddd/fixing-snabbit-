package com.snabbit.prototype.user.integration

import com.snabbit.prototype.user.service.AddressCommand
import com.snabbit.prototype.user.service.AddressService
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class AddressPersistenceIntegrationTest {
    @Autowired private lateinit var addresses: AddressService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanCreatedAddresses() {
        jdbc.update("DELETE FROM user_address WHERE id <> ?::uuid", SEEDED_ADDRESS_ID.toString())
        jdbc.update("DELETE FROM outbox_event")
    }

    @Test
    fun `verified address survives the database mapping and ownership lookup`() {
        val created = addresses.create(
            SEEDED_USER_ID,
            AddressCommand(
                "Parents",
                "20 Integration Test Road",
                "Mumbai",
                "400002",
                BigDecimal("19.07609"),
                BigDecimal("72.877426"),
                BigDecimal("15"),
                Instant.now(),
            ),
        )

        assertEquals(created, addresses.requireOwnedVerified(SEEDED_USER_ID, created.addressId))
        assertEquals(true, created.verified)
        assertEquals(2, addresses.list(SEEDED_USER_ID).size)
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'UserAddressCreated'",
                Int::class.java,
                SEEDED_USER_ID.toString(),
            ),
        )
    }

    companion object {
        val SEEDED_USER_ID: UUID = UUID.fromString("40000000-0000-0000-0000-000000000001")
        val SEEDED_ADDRESS_ID: UUID = UUID.fromString("42000000-0000-0000-0000-000000000001")

        @Container @JvmStatic
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
