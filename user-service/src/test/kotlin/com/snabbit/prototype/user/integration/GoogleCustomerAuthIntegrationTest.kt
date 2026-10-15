package com.snabbit.prototype.user.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.platform.GoogleIdentityVerifier
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(
    properties = [
        "auth.google.verifier=stub",
        "auth.google.customer-client-ids=customer-integration-client",
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(GoogleCustomerAuthIntegrationTest.TestIdentityConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class GoogleCustomerAuthIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `Google sign-in refresh logout and logout-all use persisted rotating sessions`() {
        val first = signIn("customer-id-token", "phone-one", "Customer device")
        val repeated = signIn("customer-id-token", "phone-two", "Second device")
        assertEquals(first.path("user").path("id").asText(), repeated.path("user").path("id").asText())
        assertNotEquals(first.path("refreshToken").asText(), repeated.path("refreshToken").asText())

        val refreshed = parallelRefresh(first.path("refreshToken").asText())
        assertNotEquals(first.path("refreshToken").asText(), refreshed.path("refreshToken").asText())

        val jwt = decoder().decode(refreshed.path("accessToken").asText())
        mvc.post("/api/v1/auth/logout") {
            header("X-Authenticated-User-Id", jwt.subject)
            header("X-Authenticated-Session-Id", jwt.getClaimAsString("sid"))
        }.andExpect { status { isNoContent() } }

        mvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(mapOf("refreshToken" to refreshed.path("refreshToken").asText()))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SESSION_REVOKED") }
        }

        val third = signIn("customer-id-token", "phone-three", "Third device")
        val fourth = signIn("customer-id-token", "phone-four", "Fourth device")
        val thirdJwt = decoder().decode(third.path("accessToken").asText())
        mvc.post("/api/v1/auth/logout-all") {
            header("X-Authenticated-User-Id", thirdJwt.subject)
        }.andExpect { status { isNoContent() } }

        listOf(third, fourth).forEach { session ->
            mvc.post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsBytes(mapOf("refreshToken" to session.path("refreshToken").asText()))
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("SESSION_REVOKED") }
            }
        }
    }

    private fun signIn(idToken: String, deviceId: String, deviceName: String): JsonNode =
        post(
            "/api/v1/auth/google",
            mapOf("idToken" to idToken, "deviceId" to deviceId, "deviceName" to deviceName),
        )

    private fun parallelRefresh(refreshToken: String): JsonNode {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val futures = List(2) {
                pool.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        mvc.post("/api/v1/auth/refresh") {
                            contentType = MediaType.APPLICATION_JSON
                            content = mapper.writeValueAsBytes(mapOf("refreshToken" to refreshToken))
                        }.andReturn().response
                    },
                )
            }
            ready.await()
            start.countDown()
            val results = futures.map { it.get() }
            assertEquals(listOf(200, 409), results.map { it.status }.sorted())
            assertEquals("REFRESH_IN_PROGRESS", mapper.readTree(results.single { it.status == 409 }.contentAsByteArray).path("code").asText())
            mapper.readTree(results.single { it.status == 200 }.contentAsByteArray)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun post(path: String, body: Any): JsonNode {
        val response = mvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(body)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsByteArray
        return mapper.readTree(response)
    }

    private fun decoder(): NimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(
        SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"),
    ).macAlgorithm(MacAlgorithm.HS256).build()

    @TestConfiguration
    class TestIdentityConfiguration {
        @Bean
        fun testGoogleIdentityVerifier(): GoogleIdentityVerifier = object : GoogleIdentityVerifier {
            override fun verify(idToken: String, allowedClientIds: Set<String>, expectedNonce: String?): GoogleIdentity {
                require(idToken == "customer-id-token")
                return GoogleIdentity(
                    subject = "customer-integration-subject",
                    email = "customer-integration" + "@" + "example.test",
                    displayName = "Integration Customer",
                    pictureUrl = null,
                )
            }
        }
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)
    class KGenericContainer(imageName: String) :
        GenericContainer<KGenericContainer>(DockerImageName.parse(imageName))

    companion object {
        private const val jwtSecret = "customer-integration-session-secret-32-bytes"

        @Container
        @JvmStatic
        val postgres = KPostgreSQLContainer("postgres:17-alpine")

        @Container
        @JvmStatic
        val redis = KGenericContainer("redis:8-alpine").withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("auth.jwt-secret") { jwtSecret }
        }
    }
}
