package com.snabbit.prototype.worker.integration

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.dto.request.WorkerAvailabilityCommand
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.service.WorkerAvailabilityService
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class WorkerCompatibilityIntegrationTest {
    @Autowired
    private lateinit var compatibility: WorkerCompatibilityRepository

    @Autowired
    private lateinit var workerAvailability: WorkerAvailabilityService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `migrations seed explicit allergy and religious food capabilities`() {
        val seededWorkerId = UUID.fromString("10000000-0000-0000-0000-000000000001")

        val worker = compatibility.findById(seededWorkerId).orElseThrow()

        assertEquals(listOf("DUST", "FRAGRANCE", "PET_DANDER"), worker.supportedAllergyConstraints)
        assertTrue(worker.acceptedReligiousFoodRestrictions.contains("NO_PORK"))
    }

    @Test
    fun `worker availability upsert replaces the authenticated workers unique day`() {
        val seededWorkerId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val serviceDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1)

        val updated = workerAvailability.update(
            seededWorkerId,
            serviceDate,
            WorkerAvailabilityCommand(
                AvailabilityStatus.AVAILABLE,
                LocalTime.of(10, 0),
                LocalTime.of(16, 0),
            ),
        )
        val listed = workerAvailability.list(seededWorkerId, serviceDate, serviceDate)

        assertEquals(LocalTime.of(10, 0), updated.availableFrom)
        assertEquals(listOf(updated), listed)
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'WorkerAvailabilityUpdated'",
                Int::class.java,
                seededWorkerId.toString(),
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
