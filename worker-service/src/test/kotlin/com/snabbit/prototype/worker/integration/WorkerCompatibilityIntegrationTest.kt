package com.snabbit.prototype.worker.integration

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import com.snabbit.prototype.worker.dto.request.WorkerAvailabilityCommand
import com.snabbit.prototype.worker.repository.WorkerCompatibilityRepository
import com.snabbit.prototype.worker.service.WorkerAvailabilityService
import com.snabbit.prototype.worker.service.WorkerMatchingService
import com.snabbit.prototype.worker.config.LocalWorkerSeedInitializer
import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.worker.dto.request.MatchWorkersCommand
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
        "dev-seed.enabled=true",
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

    @Autowired
    private lateinit var devSeed: LocalWorkerSeedInitializer

    @Autowired
    private lateinit var matching: WorkerMatchingService

    @Test
    fun `development seed stays ten fully bookable workers and preserves changed ratings`() {
        devSeed.refresh()
        jdbc.update("UPDATE worker_profile SET rating_sum = 1, rating_count = 1 WHERE id = ?", DevSeedWorkerIds.SAVITA)
        devSeed.refresh()

        assertEquals(
            10,
            jdbc.queryForObject("SELECT count(*) FROM worker_profile WHERE id::text LIKE '60000000-0000-0000-0000-%'", Int::class.java),
        )
        assertEquals(
            3,
            jdbc.queryForObject(
                "SELECT count(*) FROM worker_profile WHERE id::text LIKE '60000000-0000-0000-0000-%' AND tier = 'EXPERT'", Int::class.java,
            ),
        )
        assertEquals(
            2,
            jdbc.queryForObject(
                "SELECT count(*) FROM worker_profile WHERE id::text LIKE '60000000-0000-0000-0000-%' AND tier = 'VETERAN'", Int::class.java,
            ),
        )
        assertEquals(
            5,
            jdbc.queryForObject(
                "SELECT count(*) FROM worker_profile WHERE id::text LIKE '60000000-0000-0000-0000-%' AND tier = 'STANDARD'", Int::class.java,
            ),
        )
        assertEquals(
            120,
            jdbc.queryForObject(
                "SELECT count(*) FROM worker_availability WHERE worker_id::text LIKE '60000000-0000-0000-0000-%'", Int::class.java,
            ),
        )
        assertEquals(
            40,
            jdbc.queryForObject("SELECT count(*) FROM worker_skill WHERE worker_id::text LIKE '60000000-0000-0000-0000-%' AND enabled", Int::class.java),
        )
        assertEquals(1, jdbc.queryForObject("SELECT rating_sum FROM worker_profile WHERE id = ?", Int::class.java, DevSeedWorkerIds.SAVITA))
    }

    @Test
    fun `all seeded tiers match every duration with the full household constraint set`() {
        val startsAt = ZonedDateTime.of(
            LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1),
            LocalTime.of(8, 0),
            ZoneId.of("Asia/Kolkata"),
        ).toInstant()
        val expectedByTier = mapOf("EXPERT" to 3, "VETERAN" to 2, "STANDARD" to 5)

        expectedByTier.forEach { (tier, expected) ->
            listOf(60, 120, 180, 240).forEach { duration ->
                val matches = matching.match(
                    MatchWorkersCommand(
                        "HOUSE_CLEANING", 18_000, startsAt, duration,
                        19.076090, 72.877426, 50.0, true, "NON_VEGETARIAN",
                        listOf("SEPARATE_UTENSILS"), listOf("English"),
                        listOf("DUST", "FRAGRANCE", "PET_DANDER"),
                        listOf("VEGETARIAN_ONLY", "HALAL_ONLY", "NO_BEEF", "NO_PORK"),
                        listOf("FEMALE_WORKER", "MASK_REQUIRED"), true,
                        listOf("BLEACH", "AMMONIA", "FRAGRANCE"),
                        listOf("VACUUM", "MOP", "STEAM_CLEANER", "LADDER"),
                        listOf("ELDER_SUPPORT", "HEARING_SUPPORT", "MOBILITY_SUPPORT"),
                        emptyList(), false, requiredTier = tier,
                    ),
                )
                assertEquals(expected, matches.count { it.workerId in DevSeedWorkerIds.all })
            }
        }
    }

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
