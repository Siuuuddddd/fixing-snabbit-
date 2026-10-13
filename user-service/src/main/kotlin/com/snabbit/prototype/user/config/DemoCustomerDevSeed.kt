package com.snabbit.prototype.user.config

import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.request.ActivatePlusCommand
import com.snabbit.prototype.user.favourite.FavouriteWorkerService
import com.snabbit.prototype.user.plus.PlusService
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Creates only the configured customer; OTP authentication remains unchanged. */
@Component
@Profile("local", "dev")
@ConditionalOnProperty(prefix = "dev-seed", name = ["enabled"], havingValue = "true")
class DemoCustomerDevSeed(
    private val users: UserProfileRepository,
    private val plus: PlusService,
    @param:Lazy private val favourites: FavouriteWorkerService,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val properties: UserDevSeedProperties,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        runCatching { ensure() }
    }

    @Scheduled(fixedDelayString = "${'$'}{dev-seed.retry-delay:PT1M}")
    fun retry() {
        runCatching { ensure() }
    }

    fun ensureFor(customerId: UUID) {
        val phone = properties.demoCustomerPhone.trim()
        if (phone.isBlank()) return
        val existing = users.findByPhoneE164(phone) ?: return
        if (existing.id == customerId) runCatching { ensure(existing) }
    }

    fun ensure(): UserProfile? {
        val phone = properties.demoCustomerPhone.trim()
        if (phone.isBlank()) return null
        val existing = users.findByPhoneE164(phone)
        val customer = existing ?: users.save(
            UserProfile(
                stableCustomerId(phone), phone, properties.demoCustomerName.trim(), UserStatus.ACTIVE,
                false, null, clock.instant(), clock.instant(),
            ),
        )
        ensure(customer)
        return customer
    }

    private fun ensure(customer: UserProfile) {
        ensurePlus(customer)
        ensureDefaultFavourites(customer.id)
    }

    private fun ensurePlus(customer: UserProfile) {
        val now = clock.instant()
        val refreshed = users.findById(customer.id).orElse(customer)
        val threshold = now.plus(Duration.ofDays(properties.plusExtendWhenDaysRemain.toLong()))
        if (refreshed.hasActivePlus(now) && refreshed.plusExpiresAt?.isAfter(threshold) == true) return
        val expiresAt = now.plus(Duration.ofDays(properties.plusValidityDays.toLong()))
        plus.activate(
            customer.id,
            ActivatePlusCommand(
                "dev-seed:${customer.id}:${refreshed.plusExpiresAt?.epochSecond ?: 0}:${expiresAt.epochSecond}",
                now,
                expiresAt,
                passCount = 4, passDiscountMinor = 3_000, serviceCode = null,
            ),
        )
    }

    private fun ensureDefaultFavourites(customerId: UUID) {
        jdbc.update(
            """
            INSERT INTO dev_seed_state (customer_id, defaults_completed, updated_at)
            VALUES (?, FALSE, CURRENT_TIMESTAMP)
            ON CONFLICT (customer_id) DO NOTHING
            """.trimIndent(),
            customerId,
        )
        val completed = jdbc.queryForObject(
            "SELECT defaults_completed FROM dev_seed_state WHERE customer_id = ?",
            Boolean::class.java,
            customerId,
        ) ?: false
        if (completed) return
        DevSeedWorkerIds.defaultFavourites.forEach { workerId ->
            val alreadyApplied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dev_seed_favourite_applied WHERE customer_id = ? AND worker_id = ?",
                Int::class.java,
                customerId,
                workerId,
            )!! > 0
            if (!alreadyApplied) {
                favourites.addSeededDefault(customerId, workerId)
                jdbc.update(
                    """
                    INSERT INTO dev_seed_favourite_applied (customer_id, worker_id, applied_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (customer_id, worker_id) DO NOTHING
                    """.trimIndent(),
                    customerId,
                    workerId,
                )
            }
        }
        jdbc.update(
            "UPDATE dev_seed_state SET defaults_completed = TRUE, updated_at = CURRENT_TIMESTAMP WHERE customer_id = ?",
            customerId,
        )
    }

    private fun stableCustomerId(phone: String): UUID = UUID.nameUUIDFromBytes(
        "snabbit-dev-customer:$phone".toByteArray(StandardCharsets.UTF_8),
    )
}
