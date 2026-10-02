package com.snabbit.prototype.notification.integration

import com.snabbit.prototype.notification.service.NotificationTemplate
import com.snabbit.prototype.notification.service.NotificationTransaction
import com.snabbit.prototype.notification.delivery.NotificationDeliveryStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.time.Instant
import com.snabbit.prototype.notification.domain.RecipientType

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        "notifications.delivery-poll-delay=PT24H",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class NotificationPersistenceIntegrationTest {
    @Autowired private lateinit var notifications: NotificationTransaction
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var deliveryStore: NotificationDeliveryStore

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE notification_delivery, device_registration, notification, processed_message CASCADE")
    }

    @Test
    fun `critical notification and delivery jobs commit atomically`() {
        val customerId = UUID.randomUUID()
        val result = notifications.record(
            UUID.randomUUID(), RecipientType.CUSTOMER, customerId, "BookingConfirmed",
            NotificationTemplate("Confirmed", "Worker assigned", true), "{}",
        )

        assertEquals(listOf(result), notifications.history(RecipientType.CUSTOMER, customerId, 0))
        assertEquals(
            2,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_delivery WHERE notification_id = ?",
                Int::class.java,
                result.notificationId,
            ),
        )
        val claimTime = Instant.now().plusSeconds(1)
        assertEquals(
            2,
            deliveryStore.claimBatch(UUID.randomUUID(), claimTime, claimTime.plusSeconds(30), 10).size,
        )
    }

    @Test
    fun `worker and customer histories remain isolated after recipient migration`() {
        val workerId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val workerNotification = notifications.record(
            UUID.randomUUID(), RecipientType.WORKER, workerId, "WorkerOffersCreated",
            NotificationTemplate("New job offer", "Respond now", true), "{}",
        )

        assertEquals(listOf(workerNotification), notifications.history(RecipientType.WORKER, workerId, 0))
        assertEquals(emptyList<Any>(), notifications.history(RecipientType.CUSTOMER, customerId, 0))
        assertEquals(
            "WORKER",
            jdbc.queryForObject(
                "SELECT recipient_type FROM notification WHERE id = ?",
                String::class.java,
                workerNotification.notificationId,
            ),
        )
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("notifications")
            .withUsername("snabbit")
            .withPassword("snabbit")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
