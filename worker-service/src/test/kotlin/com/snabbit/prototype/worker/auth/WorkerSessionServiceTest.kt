package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.worker.config.WorkerDevSeedProperties
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerSession
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.exception.SharedWorkerSessionRequired
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRotatedRefreshTokenRepository
import com.snabbit.prototype.worker.repository.WorkerSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerSessionServiceTest {
    private val sessions = mockk<WorkerSessionRepository>()
    private val rotated = mockk<WorkerRotatedRefreshTokenRepository>()
    private val workers = mockk<WorkerProfileRepository>()
    private val revocations = mockk<WorkerSessionRevocationStore>(relaxed = true)
    private val rateLimiter = mockk<WorkerAuthRateLimiter>(relaxed = true)
    private val now = Instant.parse("2026-10-15T04:00:00Z")
    private val email = "shared-workers" + "@" + "example.test"
    private val properties = WorkerAuthProperties(
        issuer = "https://auth.snabbit.test",
        jwtSecret = "worker-session-secret-that-is-at-least-32-bytes",
        accessTokenTtl = Duration.ofMinutes(15),
    )
    private val service = WorkerSessionService(
        sessions, rotated, workers, revocations, rateLimiter,
        WorkerAuthConfiguration().workerJwtEncoder(properties), properties,
        WorkerDevSeedProperties(enabled = true, workerLoginEmail = email),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `selected seeded profile receives a shared session`() {
        val worker = worker(DevSeedWorkerIds.SAVITA)
        val saved = slot<WorkerSession>()
        every { sessions.findActiveByWorkerId(worker.id) } returns emptyList()
        every { sessions.save(capture(saved)) } answers { firstArg() }

        val response = service.create(worker, "device", "Worker device", true)

        assertTrue(saved.captured.sharedLogin)
        assertEquals(worker.id, response.user.id)
        assertEquals(OpaqueToken.hash(response.refreshToken), saved.captured.refreshTokenHash)
    }

    @Test
    fun `switch profile revokes shared session and issues one for another seeded worker`() {
        val currentWorker = worker(DevSeedWorkerIds.SAVITA)
        val target = worker(DevSeedWorkerIds.VINEETA)
        val current = session(currentWorker.id, shared = true)
        every { sessions.findForUpdate(current.id) } returns current
        every { workers.findForUpdate(target.id) } returns target
        every { sessions.findActiveByWorkerId(target.id) } returns emptyList()
        every { sessions.save(any()) } answers { firstArg() }

        val response = service.switchProfile(currentWorker.id, current.id, target.id)

        assertEquals("PROFILE_SWITCH", current.revokeReason)
        assertEquals(target.id, response.user.id)
    }

    @Test
    fun `normal worker session cannot switch profiles`() {
        val currentWorker = worker(UUID.randomUUID(), seeded = false)
        val current = session(currentWorker.id, shared = false)
        every { sessions.findForUpdate(current.id) } returns current

        assertThrows(SharedWorkerSessionRequired::class.java) {
            service.switchProfile(currentWorker.id, current.id, DevSeedWorkerIds.SAVITA)
        }
    }

    private fun session(workerId: UUID, shared: Boolean) = WorkerSession(
        id = UUID.randomUUID(), workerId = workerId, role = "WORKER",
        refreshTokenHash = OpaqueToken.hash(UUID.randomUUID().toString()), tokenFamilyId = UUID.randomUUID(),
        deviceId = "device", deviceName = "Worker device", sharedLogin = shared,
        createdAt = now, lastUsedAt = now, idleExpiresAt = now.plus(Duration.ofDays(180)), absoluteExpiresAt = null,
    )

    private fun worker(id: UUID, seeded: Boolean = true) = WorkerProfile(
        id = id, phoneE164 = "seed-worker-${id.toString().takeLast(2)}", displayName = "Worker",
        tier = WorkerTier.STANDARD, status = WorkerStatus.ACTIVE,
        currentLatitude = BigDecimal.ZERO, currentLongitude = BigDecimal.ZERO,
        serviceRadiusKm = BigDecimal.TEN, ratingSum = 0, ratingCount = 0,
        createdAt = now, updatedAt = now, joinedAt = now,
        email = email, emailVerified = true, seeded = seeded,
    )
}
