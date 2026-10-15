package com.snabbit.prototype.user.integration

import com.snabbit.prototype.user.config.DemoCustomerDevSeed
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.response.PublicWorkerProfileSnapshot
import com.snabbit.prototype.user.favourite.FavouriteWorkerClient
import com.snabbit.prototype.user.favourite.FavouriteWorkerService
import com.snabbit.prototype.user.exception.FavouriteLimitReached
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import com.snabbit.prototype.user.auth.CustomerGoogleAccountService
import com.snabbit.prototype.platform.GoogleIdentity
import io.mockk.every
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`

@SpringBootTest(
    properties = [
        "dev-seed.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class DemoCustomerDevSeedIntegrationTest {
    @Autowired private lateinit var seed: DemoCustomerDevSeed
    @Autowired private lateinit var users: UserProfileRepository
    @Autowired private lateinit var favourites: FavouriteWorkerRepository
    @Autowired private lateinit var favouriteService: FavouriteWorkerService
    @Autowired private lateinit var googleAccounts: CustomerGoogleAccountService

    @MockBean
    private lateinit var workerClient: FavouriteWorkerClient

    @Test
    fun `demo customer is active not overwritten renewed and defaults never resurrect`() {
        `when`(workerClient.profiles(anyList(), any())).thenAnswer { invocation ->
            invocation.getArgument<List<UUID>>(0).map { workerId ->
                PublicWorkerProfileSnapshot(
                    workerId, "Seed", "EXPERT", BigDecimal("4.8"), 100, 100, 12,
                    BigDecimal("4.75"), null,
                )
            }
        }

        val customer = seed.ensure()!!
        assertEquals(UserStatus.ACTIVE, customer.status)
        assertEquals(null, customer.phoneE164)
        assertEquals(null, customer.googleSub)
        assertTrue(users.findById(customer.id).orElseThrow().hasActivePlus(Instant.now()))
        assertEquals(3, favourites.findAllByIdUserIdOrderByPriority(customer.id).size)

        val linked = googleAccounts.resolve(GoogleIdentity("demo-subject", demoEmail, "Demo Customer", null))
        assertEquals(customer.id, linked.profile.id)
        assertEquals("demo-subject", linked.profile.googleSub)

        val existing = users.findById(customer.id).orElseThrow()
        existing.displayName = "Existing Customer"
        existing.plusExpiresAt = Instant.now().plus(Duration.ofDays(1))
        users.saveAndFlush(existing)
        val removed = favourites.findAllByIdUserIdOrderByPriority(customer.id).last()
        favourites.delete(removed)
        favourites.flush()

        seed.ensure()

        val refreshed = users.findById(customer.id).orElseThrow()
        assertEquals("Existing Customer", refreshed.displayName)
        assertTrue(refreshed.plusExpiresAt!!.isAfter(Instant.now().plus(Duration.ofDays(300))))
        assertEquals(2, favourites.findAllByIdUserIdOrderByPriority(customer.id).size)
    }

    @Test
    fun `concurrent favourite additions cannot exceed configured maximum`() {
        `when`(workerClient.profiles(anyList(), any())).thenAnswer { invocation ->
            invocation.getArgument<List<UUID>>(0).map { workerId ->
                PublicWorkerProfileSnapshot(
                    workerId, "Seed", "EXPERT", BigDecimal("4.8"), 100, 100, 12,
                    BigDecimal("4.75"), null,
                )
            }
        }
        val now = Instant.now()
        val customer = users.saveAndFlush(
            UserProfile(
                UUID.randomUUID(), "seed-concurrent", "Concurrent", UserStatus.ACTIVE,
                true, now.plus(Duration.ofDays(1)), now, now,
            ),
        )
        val workerIds = (1..4).map { UUID.randomUUID() }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(
                workerIds.map { workerId ->
                    Callable { runCatching { favouriteService.addSeededDefault(customer.id, workerId) }.exceptionOrNull() }
                },
            ).map { it.get() }

            assertEquals(3, favourites.findAllByIdUserIdOrderByPriority(customer.id).size)
            assertEquals(1, results.count { it is FavouriteLimitReached })
        } finally {
            pool.shutdownNow()
        }
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

    companion object {
        private val demoEmail = "demo-customer" + "@" + "example.test"

        @Container
        @JvmStatic
        val postgres = KPostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("dev-seed.demo-customer-email") { demoEmail }
        }
    }
}
