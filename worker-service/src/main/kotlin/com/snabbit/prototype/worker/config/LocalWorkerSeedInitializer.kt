package com.snabbit.prototype.worker.config

import com.snabbit.prototype.platform.DevSeedWorkerIds
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.sql.Timestamp
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Opt-in, repeatable development fixtures. This bean cannot exist in the production profile. */
@Component
@Profile("local", "dev")
@ConditionalOnProperty(prefix = "dev-seed", name = ["enabled"], havingValue = "true")
class LocalWorkerSeedInitializer(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val properties: WorkerDevSeedProperties,
) : ApplicationRunner {
    private val zone = ZoneId.of("Asia/Kolkata")

    override fun run(args: ApplicationArguments) = refresh()

    @Scheduled(cron = "${'$'}{dev-seed.refresh-cron:0 10 0 * * *}", zone = "Asia/Kolkata")
    fun dailyRefresh() = refresh()

    fun refresh() {
        val today = LocalDate.now(clock.withZone(zone))
        workers.forEachIndexed { index, worker -> upsertWorker(worker, index, today) }
        refreshAvailability(today)
    }

    private fun upsertWorker(worker: SeedWorker, index: Int, today: LocalDate) {
        val joinedAt = today.minusMonths(worker.months.toLong()).atStartOfDay(zone).toInstant()
        val placeholderPhone = "seed-worker-${(index + 1).toString().padStart(2, '0')}"
        val phone = if (worker.id == DevSeedWorkerIds.SAVITA && properties.workerLoginPhone.isNotBlank()) {
            properties.workerLoginPhone
        } else {
            placeholderPhone
        }
        jdbc.update(
            """
            INSERT INTO worker_profile (
                id, phone_e164, display_name, tier, status, current_latitude, current_longitude,
                service_radius_km, rating_sum, rating_count, created_at, updated_at, joined_at
            ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
            ON CONFLICT (id) DO UPDATE SET
                phone_e164 = EXCLUDED.phone_e164, display_name = EXCLUDED.display_name,
                tier = EXCLUDED.tier, status = 'ACTIVE', current_latitude = EXCLUDED.current_latitude,
                current_longitude = EXCLUDED.current_longitude,
                service_radius_km = EXCLUDED.service_radius_km, joined_at = EXCLUDED.joined_at,
                updated_at = CURRENT_TIMESTAMP, version = worker_profile.version + 1
            """.trimIndent(),
            worker.id, phone, worker.name, worker.tier, properties.latitude, properties.longitude,
            properties.serviceRadiusKm, worker.ratingSum, worker.ratingCount, Timestamp.from(joinedAt),
        )
        servicePrices.forEach { (serviceCode, price) ->
            jdbc.update(
                """
                INSERT INTO worker_skill (worker_id, service_code, base_price_per_hour_minor, enabled)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (worker_id, service_code) DO UPDATE SET
                    base_price_per_hour_minor = EXCLUDED.base_price_per_hour_minor, enabled = TRUE
                """.trimIndent(),
                worker.id, serviceCode, price,
            )
        }
        jdbc.update(
            """
            INSERT INTO worker_compatibility (
                worker_id, accepts_pets, languages, accepted_food_contexts, supported_utensil_preferences,
                supported_worker_constraints, supported_allergy_constraints,
                accepted_religious_food_restrictions, can_heavy_lift, available_equipment,
                avoided_chemicals, accessibility_capabilities, updated_at
            ) VALUES (
                ?, TRUE, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
                TRUE, ?::jsonb, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP
            )
            ON CONFLICT (worker_id) DO UPDATE SET
                accepts_pets = TRUE, languages = EXCLUDED.languages,
                accepted_food_contexts = EXCLUDED.accepted_food_contexts,
                supported_utensil_preferences = EXCLUDED.supported_utensil_preferences,
                supported_worker_constraints = EXCLUDED.supported_worker_constraints,
                supported_allergy_constraints = EXCLUDED.supported_allergy_constraints,
                accepted_religious_food_restrictions = EXCLUDED.accepted_religious_food_restrictions,
                can_heavy_lift = TRUE, available_equipment = EXCLUDED.available_equipment,
                avoided_chemicals = EXCLUDED.avoided_chemicals,
                accessibility_capabilities = EXCLUDED.accessibility_capabilities,
                updated_at = CURRENT_TIMESTAMP, version = worker_compatibility.version + 1
            """.trimIndent(),
            worker.id,
            "[\"Hindi\",\"Marathi\",\"English\"]",
            "[\"VEGETARIAN\",\"NON_VEGETARIAN\"]",
            "[\"SEPARATE_UTENSILS\",\"SHARED_UTENSILS\"]",
            "[\"FEMALE_WORKER\",\"MASK_REQUIRED\"]",
            "[\"DUST\",\"FRAGRANCE\",\"PET_DANDER\"]",
            "[\"VEGETARIAN_ONLY\",\"HALAL_ONLY\",\"NO_BEEF\",\"NO_PORK\"]",
            "[\"VACUUM\",\"MOP\",\"STEAM_CLEANER\",\"LADDER\"]",
            "[\"BLEACH\",\"AMMONIA\",\"FRAGRANCE\"]",
            "[\"ELDER_SUPPORT\",\"HEARING_SUPPORT\",\"MOBILITY_SUPPORT\"]",
        )
    }

    private fun refreshAvailability(today: LocalDate) {
        DevSeedWorkerIds.all.forEach { workerId ->
            repeat(properties.bookingWindowDays + properties.marginDays) { offset ->
                jdbc.update(
                    """
                    INSERT INTO worker_availability (
                        id, worker_id, service_date, available_from, available_until, status
                    ) VALUES (?, ?, ?, '08:00', '19:00', 'AVAILABLE')
                    ON CONFLICT (worker_id, service_date) DO UPDATE SET
                        available_from = '08:00', available_until = '19:00', status = 'AVAILABLE',
                        version = worker_availability.version + 1
                    """.trimIndent(),
                    UUID.randomUUID(), workerId, today.plusDays(offset.toLong()),
                )
            }
        }
    }

    private data class SeedWorker(
        val id: UUID,
        val name: String,
        val months: Int,
        val tier: String,
        val ratingSum: Int,
        val ratingCount: Int,
    )

    private val workers = listOf(
        SeedWorker(DevSeedWorkerIds.SAVITA, "Savita", 12, "EXPERT", 686, 140),
        SeedWorker(DevSeedWorkerIds.VINEETA, "Vineeta", 11, "EXPERT", 576, 120),
        SeedWorker(DevSeedWorkerIds.DEEPIKA, "Deepika", 10, "EXPERT", 447, 95),
        SeedWorker(DevSeedWorkerIds.SANGEETA, "Sangeeta", 8, "VETERAN", 528, 110),
        SeedWorker(DevSeedWorkerIds.ASHA, "Asha", 7, "VETERAN", 362, 77),
        SeedWorker(DevSeedWorkerIds.VAISHALI, "Vaishali", 5, "STANDARD", 267, 58),
        SeedWorker(DevSeedWorkerIds.SANA, "Sana", 4, "STANDARD", 152, 33),
        SeedWorker(DevSeedWorkerIds.SAILEE, "Sailee", 3, "STANDARD", 86, 19),
        SeedWorker(DevSeedWorkerIds.SHIVANI, "Shivani", 2, "STANDARD", 54, 12),
        SeedWorker(DevSeedWorkerIds.ALISHA, "Alisha", 1, "STANDARD", 35, 8),
    )

    private val servicePrices = mapOf(
        "HOUSE_CLEANING" to 18_000L,
        "DISH_WASHING" to 12_000L,
        "LAUNDRY" to 14_000L,
        "TOILET_CLEANING" to 16_000L,
    )
}
