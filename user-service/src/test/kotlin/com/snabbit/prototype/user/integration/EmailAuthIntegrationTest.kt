package com.snabbit.prototype.user.integration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.platform.GoogleIdentityVerifier
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.auth.AuthEmailSender
import com.snabbit.prototype.user.repository.UserProfileRepository
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.platform.DevSeedWorkerIds
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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
        "auth.google.customer-client-ids=email-auth-client",
        "email-auth.resend-cooldown=PT0S",
        "email-auth.sender=capturing",
        "email-auth.rate-limit-per-identity=100",
        "email-auth.rate-limit-per-ip=1000",
        "spring.kafka.listener.auto-startup=false",
        "user-eventing.poll-delay=PT1H",
    ],
)
@AutoConfigureMockMvc
@Import(EmailAuthIntegrationTest.TestAuthConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class EmailAuthIntegrationTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var users: UserProfileRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var sender: AuthEmailSender

    @Test
    fun `register verify login reset and change password preserve session rules`() {
        val email = uniqueEmail("flow")
        val password = credential()
        val replacement = credential()
        val finalPassword = credential()

        post("/api/v1/auth/register", registerBody(email, password, false), 422, "TERMS_NOT_ACCEPTED")
        post("/api/v1/auth/register", registerBody(email, "x", true), 422, "WEAK_PASSWORD")
        post("/api/v1/auth/register", registerBody(email, password, true), 202)

        post("/api/v1/auth/login", loginBody(email, password), 403, "EMAIL_NOT_VERIFIED")
        val verified = post(
            "/api/v1/auth/verify-email",
            mapOf("email" to email, "code" to mail().verificationCode(email), "deviceId" to "device-a"),
            200,
        )
        assertTrue(verified.path("user").path("emailVerified").asBoolean())
        assertTrue(verified.path("user").path("hasPassword").asBoolean())
        assertTrue(verified.path("user").path("authProviders").any { it.asText() == "PASSWORD" })

        post("/api/v1/auth/login", loginBody(email, credential()), 401, "INVALID_CREDENTIALS")
        post("/api/v1/auth/login", loginBody(uniqueEmail("unknown"), credential()), 401, "INVALID_CREDENTIALS")
        val secondSession = post("/api/v1/auth/login", loginBody(email, password), 200)

        post("/api/v1/auth/forgot-password", mapOf("email" to email), 202)
        post(
            "/api/v1/auth/reset-password",
            mapOf("email" to email, "code" to mail().resetCode(email), "newPassword" to replacement),
            204,
        )
        post("/api/v1/auth/refresh", mapOf("refreshToken" to secondSession.path("refreshToken").asText()), 401, "SESSION_REVOKED")

        val current = post("/api/v1/auth/login", loginBody(email, replacement), 200)
        val other = post("/api/v1/auth/login", loginBody(email, replacement), 200)
        val jwt = decoder().decode(current.path("accessToken").asText())
        val changed = mvc.post("/api/v1/auth/change-password") {
            header("X-Authenticated-User-Id", jwt.subject)
            header("X-Authenticated-Session-Id", jwt.getClaimAsString("sid"))
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(mapOf("currentPassword" to replacement, "newPassword" to finalPassword))
        }.andExpect { status { isOk() } }.andReturn().response.contentAsByteArray.let(mapper::readTree)
        assertFalse(changed.path("refreshToken").asText().isBlank())
        post("/api/v1/auth/refresh", mapOf("refreshToken" to other.path("refreshToken").asText()), 401, "SESSION_REVOKED")
        post("/api/v1/auth/login", loginBody(email, finalPassword), 200)
    }

    @Test
    fun `codes are replaced expire burn after five attempts and enforce hourly sends`() {
        val email = uniqueEmail("codes")
        val password = credential()
        val registerResponse = post("/api/v1/auth/register", registerBody(email, password, true), 202)
        val oldCode = mail().verificationCode(email)
        assertFalse(registerResponse.toString().contains(oldCode))
        val storedHmac = jdbc.queryForObject(
            "SELECT code_hmac FROM auth_email_challenge WHERE user_id = (SELECT id FROM user_profile WHERE email = ?)",
            String::class.java,
            email,
        )!!
        assertTrue(storedHmac.length == 64)
        assertFalse(storedHmac.contains(oldCode))
        val outboxPayloads = jdbc.queryForList("SELECT payload::text FROM outbox_event", String::class.java)
        assertFalse(outboxPayloads.any { it.contains(oldCode) || it.contains(email) })
        post("/api/v1/auth/resend-verification", mapOf("email" to email), 202)
        val currentCode = mail().verificationCode(email)
        assertFalse(oldCode == currentCode)
        post("/api/v1/auth/verify-email", mapOf("email" to email, "code" to oldCode), 401, "INVALID_OR_EXPIRED_CODE")

        repeat(3) { post("/api/v1/auth/verify-email", mapOf("email" to email, "code" to "000000"), 401, "INVALID_OR_EXPIRED_CODE") }
        post("/api/v1/auth/verify-email", mapOf("email" to email, "code" to "000000"), 429, "TOO_MANY_ATTEMPTS")
        post("/api/v1/auth/verify-email", mapOf("email" to email, "code" to currentCode), 401, "INVALID_OR_EXPIRED_CODE")

        val expiryEmail = uniqueEmail("expiry")
        post("/api/v1/auth/register", registerBody(expiryEmail, credential(), true), 202)
        jdbc.update("UPDATE auth_email_challenge SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE user_id = (SELECT id FROM user_profile WHERE email = ?)", expiryEmail)
        post("/api/v1/auth/verify-email", mapOf("email" to expiryEmail, "code" to mail().verificationCode(expiryEmail)), 401, "INVALID_OR_EXPIRED_CODE")

        val capEmail = uniqueEmail("cap")
        post("/api/v1/auth/register", registerBody(capEmail, credential(), true), 202)
        repeat(4) { post("/api/v1/auth/resend-verification", mapOf("email" to capEmail), 202) }
        post("/api/v1/auth/resend-verification", mapOf("email" to capEmail), 429, "RATE_LIMITED")
    }

    @Test
    fun `Google verification removes an unverified pre-registered password`() {
        val email = uniqueEmail("link")
        TestGoogleIdentityVerifier.email = email
        post("/api/v1/auth/register", registerBody(email, credential(), true), 202)
        val result = post("/api/v1/auth/google", mapOf("idToken" to "test-google-token"), 200)
        assertFalse(result.path("user").path("hasPassword").asBoolean())
        val profile = users.findByEmailIgnoreCase(email)!!
        assertTrue(profile.passwordHash == null)
        assertTrue(profile.emailVerified)
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM customer_auth_provider WHERE user_id = ? AND provider = 'GOOGLE'", Int::class.java, profile.id) == 1)
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM customer_auth_provider WHERE user_id = ? AND provider = 'PASSWORD'", Int::class.java, profile.id) == 0)
    }

    @Test
    fun `Google-only customer can add a password through reset and duplicate registration is generic`() {
        val email = uniqueEmail("google")
        TestGoogleIdentityVerifier.email = email
        post("/api/v1/auth/google", mapOf("idToken" to "test-google-token"), 200)
        post("/api/v1/auth/register", registerBody(email, credential(), true), 202)
        assertTrue(mail().existingNoticeRecipients.contains(email))

        val password = credential()
        post("/api/v1/auth/forgot-password", mapOf("email" to email), 202)
        post(
            "/api/v1/auth/reset-password",
            mapOf("email" to email, "code" to mail().resetCode(email), "newPassword" to password),
            204,
        )
        val session = post("/api/v1/auth/login", loginBody(email, password), 200)
        assertTrue(session.path("user").path("authProviders").map { it.asText() }.toSet() == setOf("GOOGLE", "PASSWORD"))
    }

    @Test
    fun `pre-provisioned development customer keeps identity Plus and favourites after password verification`() {
        val email = uniqueEmail("seed")
        val now = Instant.now()
        val customerId = UUID.randomUUID()
        users.saveAndFlush(
            UserProfile(
                id = customerId, phoneE164 = null, displayName = "Seed Customer", status = UserStatus.ACTIVE,
                plusActive = true, plusExpiresAt = now.plus(Duration.ofDays(90)), createdAt = now, updatedAt = now,
                email = email,
            ),
        )
        DevSeedWorkerIds.defaultFavourites.forEachIndexed { index, workerId ->
            jdbc.update(
                "INSERT INTO favourite_worker (user_id, worker_id, priority, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                customerId, workerId, index + 1,
            )
        }

        post("/api/v1/auth/register", registerBody(email, credential(), true), 202)
        val session = post(
            "/api/v1/auth/verify-email",
            mapOf("email" to email, "code" to mail().verificationCode(email)),
            200,
        )
        assertTrue(session.path("user").path("id").asText() == customerId.toString())
        val persisted = users.findById(customerId).orElseThrow()
        assertTrue(persisted.plusActive)
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM favourite_worker WHERE user_id = ?", Int::class.java, customerId) == 3)
    }

    @Test
    fun `disabled customer is rejected only after a correct password`() {
        val email = uniqueEmail("disabled")
        val password = credential()
        post("/api/v1/auth/register", registerBody(email, password, true), 202)
        post("/api/v1/auth/verify-email", mapOf("email" to email, "code" to mail().verificationCode(email)), 200)
        jdbc.update("UPDATE user_profile SET status = 'SUSPENDED' WHERE email = ?", email)
        post("/api/v1/auth/login", loginBody(email, credential()), 401, "INVALID_CREDENTIALS")
        post("/api/v1/auth/login", loginBody(email, password), 403, "ACCOUNT_DISABLED")
    }

    private fun registerBody(email: String, password: String, terms: Boolean) = mapOf(
        "name" to "Test Customer", "email" to email, "password" to password, "termsAccepted" to terms,
    )

    private fun loginBody(email: String, password: String) = mapOf("email" to email, "password" to password)

    private fun post(path: String, body: Any, status: Int, code: String? = null): JsonNode {
        val response = mvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsBytes(body)
        }.andExpect { status { isEqualTo(status) } }.andReturn().response.contentAsByteArray
        val node = if (response.isEmpty()) mapper.createObjectNode() else mapper.readTree(response)
        if (code != null) assertTrue(node.path("code").asText() == code, node.toString())
        return node
    }

    private fun credential(): String = OpaqueToken.generate().take(24) + "aA1!"
    private fun uniqueEmail(prefix: String): String = "$prefix-${OpaqueToken.generate().take(10).lowercase()}@example.test"
    private fun mail(): CapturingAuthEmailSender = sender as CapturingAuthEmailSender

    private fun decoder(): NimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(
        SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"),
    ).macAlgorithm(MacAlgorithm.HS256).build()

    @TestConfiguration
    class TestAuthConfiguration {
        @Bean
        @Primary
        fun capturingAuthEmailSender(): AuthEmailSender = CapturingAuthEmailSender()

        @Bean
        @Primary
        fun emailAuthGoogleVerifier(): GoogleIdentityVerifier = TestGoogleIdentityVerifier
    }

    object TestGoogleIdentityVerifier : GoogleIdentityVerifier {
        @Volatile var email: String = "unused@example.test"
        override fun verify(idToken: String, allowedClientIds: Set<String>, expectedNonce: String?): GoogleIdentity {
            require(idToken == "test-google-token")
            return GoogleIdentity("subject-${email.substringBefore('@')}", email, "Google Customer", null)
        }
    }

    class CapturingAuthEmailSender : AuthEmailSender {
        private val verificationCodes = ConcurrentHashMap<String, String>()
        private val resetCodes = ConcurrentHashMap<String, String>()
        val existingNoticeRecipients = ConcurrentHashMap.newKeySet<String>()
        override fun sendVerificationCode(email: String, code: String) { verificationCodes[email] = code }
        override fun sendResetCode(email: String, code: String) { resetCodes[email] = code }
        override fun sendExistingAccountNotice(email: String) { existingNoticeRecipients.add(email) }
        fun verificationCode(email: String): String = requireNotNull(verificationCodes[email])
        fun resetCode(email: String): String = requireNotNull(resetCodes[email])
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)
    class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(DockerImageName.parse(imageName))

    companion object {
        private val jwtSecret = OpaqueToken.generate() + OpaqueToken.generate()
        private val hmacSecret = OpaqueToken.generate() + OpaqueToken.generate()

        @Container @JvmStatic val postgres = KPostgreSQLContainer("postgres:17-alpine")
        @Container @JvmStatic val redis = KGenericContainer("redis:8-alpine").withExposedPorts(6379)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("auth.jwt-secret") { jwtSecret }
            registry.add("email-auth.code-hmac-secret") { hmacSecret }
        }
    }
}
