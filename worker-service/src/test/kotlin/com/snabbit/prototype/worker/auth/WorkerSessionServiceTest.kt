package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.worker.config.WorkerDevSeedProperties
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerRotatedRefreshToken
import com.snabbit.prototype.worker.domain.WorkerSession
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.exception.SharedWorkerSessionRequired
import com.snabbit.prototype.worker.exception.WorkerRefreshInProgress
import com.snabbit.prototype.worker.exception.WorkerSessionRevoked
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRotatedRefreshTokenRepository
import com.snabbit.prototype.worker.repository.WorkerSessionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    private val clock = MutableClock(now)
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
        clock,
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

    @Test
    fun `refresh rotates once and a race loser receives the grace response`() {
        val worker = worker(UUID.randomUUID(), seeded = false)
        val originalHash = OpaqueToken.hash("original-worker-refresh")
        val session = session(worker.id, shared = false, refreshHash = originalHash)
        every { sessions.findByRefreshHashForUpdate(originalHash) } returns session andThen null
        every { workers.findForUpdate(worker.id) } returns worker
        every { rotated.save(any()) } answers { firstArg() }

        val response = service.refresh("original-worker-refresh")
        val used = WorkerRotatedRefreshToken(UUID.randomUUID(), session.id, originalHash, now)
        every { rotated.findByTokenHash(originalHash) } returns used
        every { sessions.findForUpdate(session.id) } returns session

        assertNotEquals("original-worker-refresh", response.refreshToken)
        assertEquals(OpaqueToken.hash(response.refreshToken), session.refreshTokenHash)
        assertThrows(WorkerRefreshInProgress::class.java) { service.refresh("original-worker-refresh") }
    }

    @Test
    fun `refresh reuse after grace revokes the worker token family`() {
        val usedHash = OpaqueToken.hash("used-worker-refresh")
        val worker = worker(UUID.randomUUID(), seeded = false)
        val session = session(worker.id, shared = false)
        val used = WorkerRotatedRefreshToken(UUID.randomUUID(), session.id, usedHash, now)
        clock.advance(Duration.ofSeconds(11))
        every { sessions.findByRefreshHashForUpdate(usedHash) } returns null
        every { rotated.findByTokenHash(usedHash) } returns used
        every { sessions.findForUpdate(session.id) } returns session
        every { sessions.findByTokenFamilyId(session.tokenFamilyId) } returns listOf(session)

        assertThrows(WorkerSessionRevoked::class.java) { service.refresh("used-worker-refresh") }
        assertEquals("REFRESH_REUSE", session.revokeReason)
        verify { revocations.revoke(session.id, properties.accessTokenTtl) }
    }

    @Test
    fun `expiry logout logout-all and cap eviction revoke worker sessions`() {
        val worker = worker(UUID.randomUUID(), seeded = false)
        val expired = session(worker.id, shared = false).apply { idleExpiresAt = now.minusSeconds(1) }
        every { sessions.findByRefreshHashForUpdate(any()) } returns expired
        every { workers.findForUpdate(worker.id) } returns worker
        assertThrows(WorkerSessionRevoked::class.java) { service.refresh("expired-worker-refresh") }

        val logout = session(worker.id, shared = false)
        every { sessions.findForUpdate(logout.id) } returns logout
        service.logout(worker.id, logout.id)
        assertEquals("LOGOUT", logout.revokeReason)

        val all = listOf(session(worker.id, false), session(worker.id, false))
        every { workers.findForUpdate(worker.id) } returns worker
        every { sessions.findActiveByWorkerId(worker.id) } returns all
        service.logoutAll(worker.id)
        assertEquals(now, worker.sessionsValidAfter)
        assertEquals(listOf("LOGOUT_ALL", "LOGOUT_ALL"), all.map(WorkerSession::revokeReason))

        val atLimit = List(10) { index -> session(worker.id, false, createdAt = now.plusSeconds(index.toLong())) }
        every { sessions.findActiveByWorkerId(worker.id) } returns atLimit
        every { sessions.save(any()) } answers { firstArg() }
        service.create(worker, null, null, false)
        assertEquals("SESSION_LIMIT", atLimit.first().revokeReason)
    }

    @Test
    fun `absolute expiry and account cutoff invalidate worker refresh sessions`() {
        val worker = worker(UUID.randomUUID(), seeded = false)
        val absoluteExpired = session(worker.id, false, absoluteExpiresAt = now.minusSeconds(1))
        every { sessions.findByRefreshHashForUpdate(any()) } returns absoluteExpired
        every { workers.findForUpdate(worker.id) } returns worker
        assertThrows(WorkerSessionRevoked::class.java) { service.refresh("absolute-expired") }
        assertEquals("SESSION_EXPIRED", absoluteExpired.revokeReason)

        val beforeCutoff = session(worker.id, false, createdAt = now.minusSeconds(1))
        worker.sessionsValidAfter = now
        every { sessions.findByRefreshHashForUpdate(any()) } returns beforeCutoff
        assertThrows(WorkerSessionRevoked::class.java) { service.refresh("before-cutoff") }
        assertEquals("SESSION_EXPIRED", beforeCutoff.revokeReason)
    }

    private fun session(
        workerId: UUID,
        shared: Boolean,
        refreshHash: String = OpaqueToken.hash(UUID.randomUUID().toString()),
        createdAt: Instant = now,
        absoluteExpiresAt: Instant? = null,
    ) = WorkerSession(
        id = UUID.randomUUID(), workerId = workerId, role = "WORKER",
        refreshTokenHash = refreshHash, tokenFamilyId = UUID.randomUUID(),
        deviceId = "device", deviceName = "Worker device", sharedLogin = shared,
        createdAt = createdAt, lastUsedAt = createdAt,
        idleExpiresAt = createdAt.plus(Duration.ofDays(180)), absoluteExpiresAt = absoluteExpiresAt,
    )

    private fun worker(id: UUID, seeded: Boolean = true) = WorkerProfile(
        id = id, phoneE164 = "seed-worker-${id.toString().takeLast(2)}", displayName = "Worker",
        tier = WorkerTier.STANDARD, status = WorkerStatus.ACTIVE,
        currentLatitude = BigDecimal.ZERO, currentLongitude = BigDecimal.ZERO,
        serviceRadiusKm = BigDecimal.TEN, ratingSum = 0, ratingCount = 0,
        createdAt = now, updatedAt = now, joinedAt = now,
        email = email, emailVerified = true, seeded = seeded,
    )

    private class MutableClock(private var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun advance(duration: Duration) { current = current.plus(duration) }
    }
}
