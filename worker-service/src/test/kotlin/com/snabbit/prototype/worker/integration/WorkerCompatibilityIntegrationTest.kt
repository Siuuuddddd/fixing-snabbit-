package com.snabbit.prototype.worker.integration

import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.domain.AvailabilityStatus
import com.snabbit.prototype.worker.service.WorkerAvailabilityCommand
import com.snabbit.prototype.worker.service.WorkerAvailabilityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class WorkerCompatibilityIntegrationTest {
    @Autowired
    private lateinit var compatibility: WorkerCompatibilityRepository

    @Autowired
    private lateinit var workerAvailability: WorkerAvailabilityService

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
