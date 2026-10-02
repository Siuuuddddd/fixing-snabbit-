package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerStatus
import com.snabbit.prototype.worker.domain.WorkerTier
import com.snabbit.prototype.worker.persistence.WorkerProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class WorkerAuthenticationServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val store = MemoryStore()
    private val delivery = mockk<WorkerOtpDelivery>()
    private val code = slot<String>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val secret = "worker-unit-test-secret-that-is-at-least-32-bytes"
    private val properties = WorkerAuthProperties(
        "https://auth.snabbit.test",
        secret,
        "pepper",
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
        Duration.ofMinutes(15),
        5,
        true,
    )
    private val service = WorkerAuthenticationService(
        workers,
        store,
        delivery,
        WorkerAuthConfiguration().workerJwtEncoder(properties),
        properties,
        Clock.fixed(now, ZoneOffset.UTC),
    )
    private val phone = "+919900000001"

    @Test
    fun `active worker receives one-time OTP and worker role token`() {
        val worker = worker()
        every { workers.findByPhoneE164(phone) } returns worker
        every { delivery.deliver(phone, capture(code)) } returns Unit

        val challenge = service.requestOtp(phone)
        val token = service.verifyOtp(phone, challenge.debugOtp!!)

        assertEquals(code.captured, challenge.debugOtp)
        val decoded = decoder().decode(token.accessToken)
        assertEquals(worker.id.toString(), decoded.subject)
        assertEquals(listOf("WORKER"), decoded.getClaimAsStringList("roles"))
        assertNull(store.value)
        assertThrows(InvalidWorkerOtp::class.java) { service.verifyOtp(phone, challenge.debugOtp!!) }
    }

    private fun decoder(): NimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(
        SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"),
    ).macAlgorithm(MacAlgorithm.HS256).build().also { decoder ->
        decoder.setJwtValidator(JwtTimestampValidator().apply { setClock(Clock.fixed(now, ZoneOffset.UTC)) })
    }

    private fun worker() = WorkerProfile(
        UUID.randomUUID(),
        phone,
        "Asha",
        WorkerTier.VETERAN,
        WorkerStatus.ACTIVE,
        BigDecimal("19.076090"),
        BigDecimal("72.877426"),
        BigDecimal("15"),
        47,
        10,
        now,
        now,
    )

    private class MemoryStore : WorkerOtpStore {
        var cooldown = false
        var value: String? = null
        var attempts = 0L

        override fun acquireCooldown(key: String, ttl: Duration): Boolean = if (cooldown) false else true.also { cooldown = true }
        override fun save(key: String, hash: String, ttl: Duration) { value = hash; attempts = 0 }
        override fun hash(key: String): String? = value
        override fun incrementAttempts(key: String, ttl: Duration): Long = ++attempts
        override fun clear(key: String) { value = null; attempts = 0 }
    }
}
