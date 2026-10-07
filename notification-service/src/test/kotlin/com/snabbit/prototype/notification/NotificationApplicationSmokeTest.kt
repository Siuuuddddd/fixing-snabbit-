package com.snabbit.prototype.notification

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        "notifications.delivery-poll-delay=PT24H",
        "notifications.push.provider=logging",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class NotificationApplicationSmokeTest {
    @Autowired private lateinit var context: ApplicationContext

    @Test
    fun `full application context supplies a RestClient builder`() {
        assertNotNull(context.getBean(RestClient.Builder::class.java))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("notifications-smoke")
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
