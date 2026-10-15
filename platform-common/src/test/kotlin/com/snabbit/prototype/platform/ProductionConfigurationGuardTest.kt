package com.snabbit.prototype.platform

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class ProductionConfigurationGuardTest {
    @Test
    fun `rejects development seed in production`() {
        val environment = MockEnvironment()
            .withProperty("dev-seed.enabled", "true")
            .withProperty("service-auth.token", "a-secure-secret-injected-by-the-runtime")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(environment, ProductionGuardProperties(listOf("service-auth.token")))
        }
    }

    @Test
    fun `rejects seeded worker automation in production`() {
        listOf("dev-seed.auto-accept-offers", "dev-seed.auto-progress").forEach { unsafeKey ->
            val environment = MockEnvironment()
                .withProperty(unsafeKey, "true")
                .withProperty("service-auth.token", "a-secure-secret-injected-by-the-runtime")
            assertThrows(IllegalArgumentException::class.java) {
                ProductionConfigurationGuard(environment, ProductionGuardProperties(listOf("service-auth.token")))
            }
        }
    }

    @Test
    fun `rejects development login emails in production even when seed is disabled`() {
        listOf("dev-seed.worker-login-email", "dev-seed.demo-customer-email").forEach { key ->
            val environment = MockEnvironment()
                .withProperty(key, "runtime" + "@" + "example.test")
                .withProperty("service-auth.token", "a-secure-secret-injected-by-the-runtime")
            assertThrows(IllegalArgumentException::class.java) {
                ProductionConfigurationGuard(environment, ProductionGuardProperties(listOf("service-auth.token")))
            }
        }
    }

    @Test
    fun `accepts explicitly supplied production values`() {
        val environment = MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://database.internal/users")
            .withProperty("service-auth.token", "a-secure-secret-injected-by-the-runtime")

        assertDoesNotThrow {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(listOf("spring.datasource.url", "service-auth.token")),
            )
        }
    }

    @Test
    fun `rejects missing and checked-in development values`() {
        val environment = MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5433/users")
            .withProperty("service-auth.token", "local-only-service-token-change-me")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(
                    listOf("spring.datasource.url", "service-auth.token", "spring.datasource.password"),
                ),
            )
        }
    }

    @Test
    fun `requires live Razorpay mode in production`() {
        val environment = MockEnvironment().withProperty("wallet.razorpay.mode", "fake")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(listOf("wallet.razorpay.mode")),
            )
        }
    }

    @Test
    fun `rejects disabled production providers`() {
        val environment = MockEnvironment()
            .withProperty("geocoding.provider", "disabled")
            .withProperty("notifications.push.provider", "logging")
            .withProperty("sms.provider", "logging")
            .withProperty("arrival-otp-provider.provider", "stub")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(
                    listOf(
                        "geocoding.provider", "notifications.push.provider", "sms.provider",
                        "arrival-otp-provider.provider",
                    ),
                ),
            )
        }
    }

    @Test
    fun `requires Google client identifiers in production`() {
        val environment = MockEnvironment()
            .withProperty("auth.google.verifier", "google")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(
                    listOf("auth.google.verifier", "auth.google.customer-client-ids"),
                ),
            )
        }
    }

    @Test
    fun `rejects a stub Google verifier in production`() {
        val environment = MockEnvironment()
            .withProperty("auth.google.verifier", "stub")
            .withProperty("auth.google.customer-client-ids", "client-id-from-runtime")

        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                environment,
                ProductionGuardProperties(
                    listOf("auth.google.verifier", "auth.google.customer-client-ids"),
                ),
            )
        }
    }

    @Test
    fun `rejects local or fake email delivery in production`() {
        listOf(
            MockEnvironment().withProperty("spring.mail.host", "mailpit"),
            MockEnvironment().withProperty("email-auth.sender", "fake"),
        ).forEach { environment ->
            val key = if (environment.getProperty("spring.mail.host") != null) "spring.mail.host" else "email-auth.sender"
            assertThrows(IllegalArgumentException::class.java) {
                ProductionConfigurationGuard(environment, ProductionGuardProperties(listOf(key)))
            }
        }
    }

    @Test
    fun `requires an email code HMAC secret in production`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductionConfigurationGuard(
                MockEnvironment(),
                ProductionGuardProperties(listOf("email-auth.code-hmac-secret")),
            )
        }
    }
}
