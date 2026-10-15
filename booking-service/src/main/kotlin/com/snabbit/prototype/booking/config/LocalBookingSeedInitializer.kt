package com.snabbit.prototype.booking.config

import com.snabbit.prototype.platform.DevSeedWorkerIds
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Opt-in rolling capacity for the ten stable development workers. */
@Component
@Profile("local", "dev")
@ConditionalOnProperty(prefix = "dev-seed", name = ["enabled"], havingValue = "true")
class LocalBookingSeedInitializer(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val properties: BookingDevSeedProperties,
) : ApplicationRunner {
    private val zone = ZoneId.of("Asia/Kolkata")

    override fun run(args: ApplicationArguments) = refresh()

    @Scheduled(cron = "${'$'}{dev-seed.refresh-cron:0 10 0 * * *}", zone = "Asia/Kolkata")
    fun dailyRefresh() = refresh()

    fun refresh() {
        val today = LocalDate.now(clock.withZone(zone))
        DevSeedWorkerIds.all.forEach { workerId ->
            repeat(properties.bookingWindowDays + properties.marginDays) { offset ->
                jdbc.update(
                    """
                    INSERT INTO worker_day_capacity (id, worker_id, service_date, available_from, available_until)
                    VALUES (?, ?, ?, '00:00', '23:59:59')
                    ON CONFLICT (worker_id, service_date) DO UPDATE SET
                        available_from = '00:00', available_until = '23:59:59'
                    """.trimIndent(),
                    UUID.randomUUID(), workerId, today.plusDays(offset.toLong()),
                )
            }
        }
    }
}
