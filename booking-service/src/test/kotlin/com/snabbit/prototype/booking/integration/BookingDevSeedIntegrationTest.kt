package com.snabbit.prototype.booking.integration

import com.snabbit.prototype.booking.config.LocalBookingSeedInitializer
import org.junit.jupiter.api.Assertions.assertEquals
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
        "dev-seed.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
        "eventing.outbox-enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class BookingDevSeedIntegrationTest {
    @Autowired private lateinit var seed: LocalBookingSeedInitializer
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `capacity seed is repeatable for ten workers across window and margin`() {
        seed.refresh()
        seed.refresh()

        assertEquals(
            120,
            jdbc.queryForObject(
                "SELECT count(*) FROM worker_day_capacity WHERE worker_id::text LIKE '60000000-0000-0000-0000-%'",
                Int::class.java,
            ),
        )
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

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
