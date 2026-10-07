package com.snabbit.prototype.notification.integration

import com.snabbit.prototype.notification.delivery.NotificationDeliveryStore
import com.snabbit.prototype.notification.delivery.NotificationDeliveryQuery
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.NotificationTemplate
import com.snabbit.prototype.notification.events.WorkerPushRecipientResolver
import com.snabbit.prototype.notification.service.NotificationTransaction
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        "notifications.delivery-poll-delay=PT24H",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class NotificationPersistenceIntegrationTest {
    @Autowired private lateinit var notifications: NotificationTransaction
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var deliveryStore: NotificationDeliveryStore
    @Autowired private lateinit var deliveryQuery: NotificationDeliveryQuery
    @Autowired private lateinit var workerRecipients: WorkerPushRecipientResolver

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

        val persisted = notifications.history(RecipientType.CUSTOMER, customerId, 0).single()
        assertEquals(result.copy(createdAt = persisted.createdAt), persisted)
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

        val persisted = notifications.history(RecipientType.WORKER, workerId, 0).single()
        assertEquals(workerNotification.copy(createdAt = persisted.createdAt), persisted)
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

    @Test
    fun `invalid FCM registration is made inactive`() {
        val customerId = UUID.randomUUID()
        val token = "invalid-firebase-installation-id-12345"
        notifications.registerDevice(RecipientType.CUSTOMER, customerId, token)

        deliveryQuery.deactivatePushTokens(setOf(token), Instant.now())

        assertEquals(
            false,
            jdbc.queryForObject(
                "SELECT active FROM device_registration WHERE push_token = ?",
                Boolean::class.java,
                token,
            ),
        )
    }

    @Test
    fun `worker assignment recipients are resolved from durable offer notifications`() {
        val workerId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        notifications.record(
            UUID.randomUUID(),
            RecipientType.WORKER,
            workerId,
            "WorkerOffersCreated",
            NotificationTemplate("Offer", "Respond", true),
            """{
                "bookingId":"$bookingId",
                "offers":[{"jobId":"$jobId","visitIndex":1,"workerId":"$workerId"}]
            }""",
        )

        val target = workerRecipients.targets(bookingId).single()

        assertEquals(workerId, target.workerId)
        assertEquals(jobId.toString(), target.jobs.single().path("jobId").asText())
        assertEquals(1, target.jobs.single().path("visitIndex").asInt())
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
