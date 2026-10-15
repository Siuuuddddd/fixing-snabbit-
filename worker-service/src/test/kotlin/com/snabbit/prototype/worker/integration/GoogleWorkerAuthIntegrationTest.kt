package com.snabbit.prototype.worker.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.platform.GoogleIdentityVerifier
import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(
    properties = [
        "auth.google.verifier=stub",
        "auth.google.worker-client-ids=worker-integration-client",
        "dev-seed.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "worker-eventing.poll-delay=PT1H",
    ],
)
@AutoConfigureMockMvc
@Import(GoogleWorkerAuthIntegrationTest.TestIdentityConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class GoogleWorkerAuthIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `shared Google login selects and switches among exactly ten persisted seeded workers`() {
        val picker = post(
            "/api/v1/worker-auth/google",
            mapOf("idToken" to "worker-id-token", "deviceId" to "worker-device", "deviceName" to "Worker device"),
        )
        assertEquals("WORKER_SELECTION_REQUIRED", picker.path("code").asText())
        assertEquals(10, picker.path("workers").size())
        assertEquals(DevSeedWorkerIds.all.toSet(), picker.path("workers").map { java.util.UUID.fromString(it.path("workerId").asText()) }.toSet())
        picker.path("workers").forEach { option -> assertEquals(true, option.path("phoneNumber").isMissingNode) }

        val selectedWorker = picker.path("workers").first().path("workerId").asText()
        val selected = post(
            "/api/v1/worker-auth/select-profile",
            mapOf(
                "idToken" to "worker-id-token",
                "selectionToken" to picker.path("selectionToken").asText(),
                "workerId" to selectedWorker,
                "deviceId" to "worker-device",
                "deviceName" to "Worker device",
            ),
        )
        assertEquals(selectedWorker, selected.path("user").path("id").asText())

        mvc.post("/api/v1/worker-auth/select-profile") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(
                mapOf(
                    "idToken" to "worker-id-token",
                    "selectionToken" to picker.path("selectionToken").asText(),
                    "workerId" to selectedWorker,
                ),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_SELECTION_TOKEN") }
        }

        val selectedJwt = decoder().decode(selected.path("accessToken").asText())
        val targetWorker = picker.path("workers").last().path("workerId").asText()
        val switchedResponse = mvc.post("/api/v1/worker-auth/switch-profile") {
            header("X-Authenticated-Worker-Id", selectedJwt.subject)
            header("X-Authenticated-Session-Id", selectedJwt.getClaimAsString("sid"))
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(mapOf("workerId" to targetWorker))
        }.andExpect { status { isOk() } }.andReturn().response.contentAsByteArray
        val switched = mapper.readTree(switchedResponse)
        assertEquals(targetWorker, switched.path("user").path("id").asText())
        assertNotEquals(selected.path("refreshToken").asText(), switched.path("refreshToken").asText())

        mvc.post("/api/v1/worker-auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(mapOf("refreshToken" to selected.path("refreshToken").asText()))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SESSION_REVOKED") }
        }

        val refreshed = post(
            "/api/v1/worker-auth/refresh",
            mapOf("refreshToken" to switched.path("refreshToken").asText()),
        )
        assertNotEquals(switched.path("refreshToken").asText(), refreshed.path("refreshToken").asText())
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
                require(idToken == "worker-id-token")
                return GoogleIdentity(
                    subject = "worker-integration-subject",
                    email = sharedEmail,
                    displayName = "Shared Worker",
                    pictureUrl = null,
                )
            }
        }
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)
    class KGenericContainer(imageName: String) :
        GenericContainer<KGenericContainer>(DockerImageName.parse(imageName))

    companion object {
        private const val jwtSecret = "worker-integration-session-secret-32-bytes"
        private val sharedEmail = "worker-integration" + "@" + "example.test"

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
            registry.add("dev-seed.worker-login-email") { sharedEmail }
        }
    }
}
