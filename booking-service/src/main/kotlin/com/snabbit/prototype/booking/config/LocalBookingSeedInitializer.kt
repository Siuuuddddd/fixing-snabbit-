package com.snabbit.prototype.booking.config

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** Adds only missing rolling capacity for stable prototype workers in local environments. */
@Component
@Profile("local")
class LocalBookingSeedInitializer(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val properties: BookingProperties? = null,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")))
        jdbc.update(
            """
            INSERT INTO worker_day_capacity (id, worker_id, service_date, available_from, available_until)
            SELECT gen_random_uuid(), worker_id, ?::date + day_offset, '08:00', '19:00'
            FROM (VALUES
                ('10000000-0000-0000-0000-000000000001'::uuid),
                ('10000000-0000-0000-0000-000000000002'::uuid)
            ) AS workers(worker_id)
            CROSS JOIN generate_series(0, ?) AS day_offset
            ON CONFLICT (worker_id, service_date) DO NOTHING
            """.trimIndent(),
            today,
            (properties?.advanceWindowDays ?: 10) - 1,
        )
    }
}
