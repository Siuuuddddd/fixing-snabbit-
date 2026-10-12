package com.snabbit.prototype.worker.config

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Value

/** Keeps prototype worker data useful across long-lived local database volumes. */
@Component
@Profile("local")
class LocalWorkerSeedInitializer(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    @param:Value("\${worker.local-seed-days:10}") private val seedDays: Int = 10,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        val today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))
        jdbc.update(
            """
            UPDATE worker_compatibility
            SET accessibility_capabilities = accessibility_capabilities || '["HEARING_SUPPORT"]'::jsonb,
                updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE worker_id = '10000000-0000-0000-0000-000000000001'::uuid
              AND NOT accessibility_capabilities @> '["HEARING_SUPPORT"]'::jsonb
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO worker_availability (id, worker_id, service_date, available_from, available_until, status)
            SELECT gen_random_uuid(), worker.id, ?::date + day_offset, '08:00', '19:00', 'AVAILABLE'
            FROM worker_profile worker
            CROSS JOIN generate_series(0, ?) AS day_offset
            WHERE worker.id IN (
                '10000000-0000-0000-0000-000000000001'::uuid,
                '10000000-0000-0000-0000-000000000002'::uuid
            )
            ON CONFLICT (worker_id, service_date) DO NOTHING
            """.trimIndent(),
            today,
            seedDays - 1,
        )
    }
}
