package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.worker.config.WorkerDevSeedProperties
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerRotatedRefreshToken
import com.snabbit.prototype.worker.domain.WorkerSession
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.dto.response.WorkerSessionResponse
import com.snabbit.prototype.worker.dto.response.WorkerSessionUser
import com.snabbit.prototype.worker.exception.SharedWorkerSessionRequired
import com.snabbit.prototype.worker.exception.WorkerRefreshInProgress
import com.snabbit.prototype.worker.exception.WorkerSessionRevoked
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.repository.WorkerRotatedRefreshTokenRepository
import com.snabbit.prototype.worker.repository.WorkerSessionRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerSessionService(
    private val sessions: WorkerSessionRepository,
    private val rotatedTokens: WorkerRotatedRefreshTokenRepository,
    private val workers: WorkerProfileRepository,
    private val revocations: WorkerSessionRevocationStore,
    private val rateLimiter: WorkerAuthRateLimiter,
    private val jwtEncoder: JwtEncoder,
    private val properties: WorkerAuthProperties,
    private val seed: WorkerDevSeedProperties,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        worker: WorkerProfile,
        deviceId: String?,
        deviceName: String?,
        sharedLogin: Boolean,
    ): WorkerSessionResponse = createInternal(worker, deviceId, deviceName, sharedLogin, clock.instant())

    @Transactional(noRollbackFor = [WorkerSessionRevoked::class, WorkerRefreshInProgress::class])
    fun refresh(rawRefresh: String): WorkerSessionResponse {
        val hash = OpaqueToken.hash(rawRefresh)
        val session = sessions.findByRefreshHashForUpdate(hash) ?: handleRotatedOrUnknown(hash)
        rateLimiter.check("refresh", session.workerId.toString())
        val now = clock.instant()
        val worker = workers.findForUpdate(session.workerId) ?: return revokeAndThrow(session, now, "ACCOUNT_MISSING")
        ensureUsable(session, worker, now)
        rotatedTokens.save(WorkerRotatedRefreshToken(UUID.randomUUID(), session.id, hash, now))
        val replacement = OpaqueToken.generate()
        session.refreshTokenHash = OpaqueToken.hash(replacement)
        session.lastUsedAt = now
        session.idleExpiresAt = minExpiry(now.plus(properties.refreshIdleTtl), session.absoluteExpiresAt)
        return response(session, worker, replacement, now)
    }

    @Transactional
    fun switchProfile(authenticatedWorkerId: UUID, sessionId: UUID, targetWorkerId: UUID): WorkerSessionResponse {
        val now = clock.instant()
        val current = sessions.findForUpdate(sessionId) ?: throw WorkerSessionRevoked()
        if (current.workerId != authenticatedWorkerId || !current.sharedLogin || current.revokedAt != null) {
            throw SharedWorkerSessionRequired()
        }
        val target = workers.findForUpdate(targetWorkerId) ?: throw SharedWorkerSessionRequired()
        if (!seed.enabled || target.id !in DevSeedWorkerIds.all || !target.seeded || target.status != WorkerStatus.ACTIVE ||
            !target.email.equals(seed.workerLoginEmail, ignoreCase = true)
        ) throw SharedWorkerSessionRequired()
        revoke(current, now, "PROFILE_SWITCH")
        return createInternal(target, current.deviceId, current.deviceName, true, now)
    }

    @Transactional
    fun logout(workerId: UUID, sessionId: UUID) {
        val now = clock.instant()
        val session = sessions.findForUpdate(sessionId) ?: return
        if (session.workerId != workerId) throw WorkerSessionRevoked()
        revoke(session, now, "LOGOUT")
    }

    @Transactional
    fun logoutAll(workerId: UUID, reason: String = "LOGOUT_ALL") = invalidateAllSessions(workerId, reason)

    @Transactional
    fun invalidateAllSessions(workerId: UUID, reason: String) {
        val now = clock.instant()
        val worker = workers.findForUpdate(workerId) ?: throw WorkerSessionRevoked()
        worker.sessionsValidAfter = now
        worker.updatedAt = now
        sessions.findActiveByWorkerId(workerId).forEach { revoke(it, now, reason) }
    }

    private fun createInternal(
        worker: WorkerProfile,
        deviceId: String?,
        deviceName: String?,
        sharedLogin: Boolean,
        now: Instant,
    ): WorkerSessionResponse {
        enforceCap(worker.id, now)
        val rawRefresh = OpaqueToken.generate()
        val session = WorkerSession(
            id = UUID.randomUUID(), workerId = worker.id, role = ROLE,
            refreshTokenHash = OpaqueToken.hash(rawRefresh), tokenFamilyId = UUID.randomUUID(),
            deviceId = clean(deviceId), deviceName = clean(deviceName), sharedLogin = sharedLogin,
            createdAt = now, lastUsedAt = now, idleExpiresAt = now.plus(properties.refreshIdleTtl),
            absoluteExpiresAt = properties.refreshAbsoluteTtl?.let(now::plus),
        )
        sessions.save(session)
        return response(session, worker, rawRefresh, now)
    }

    private fun handleRotatedOrUnknown(hash: String): WorkerSession {
        val used = rotatedTokens.findByTokenHash(hash) ?: throw WorkerSessionRevoked()
        val session = sessions.findForUpdate(used.sessionId) ?: throw WorkerSessionRevoked()
        val now = clock.instant()
        if (now.isBefore(used.rotatedAt.plus(properties.refreshGraceWindow))) throw WorkerRefreshInProgress()
        sessions.findByTokenFamilyId(session.tokenFamilyId).forEach { revoke(it, now, "REFRESH_REUSE") }
        throw WorkerSessionRevoked()
    }

    private fun ensureUsable(session: WorkerSession, worker: WorkerProfile, now: Instant) {
        if (session.revokedAt != null || worker.status != WorkerStatus.ACTIVE ||
            !now.isBefore(session.idleExpiresAt) || session.absoluteExpiresAt?.let { !now.isBefore(it) } == true ||
            session.createdAt.isBefore(worker.sessionsValidAfter)
        ) revokeAndThrow(session, now, "SESSION_EXPIRED")
    }

    private fun revokeAndThrow(session: WorkerSession, now: Instant, reason: String): Nothing {
        revoke(session, now, reason)
        throw WorkerSessionRevoked()
    }

    private fun enforceCap(workerId: UUID, now: Instant) {
        val active = sessions.findActiveByWorkerId(workerId)
        val evictCount = active.size - properties.maxActiveSessions + 1
        if (evictCount > 0) active.take(evictCount).forEach { revoke(it, now, "SESSION_LIMIT") }
    }

    private fun revoke(session: WorkerSession, now: Instant, reason: String) {
        session.revoke(now, reason)
        revocations.revoke(session.id, properties.accessTokenTtl)
    }

    private fun response(session: WorkerSession, worker: WorkerProfile, refreshToken: String, now: Instant) =
        WorkerSessionResponse(
            accessToken = accessToken(worker.id, session.id, now),
            expiresIn = properties.accessTokenTtl.seconds,
            refreshToken = refreshToken,
            user = WorkerSessionUser(
                id = worker.id, name = worker.displayName, email = requireNotNull(worker.email),
                pictureUrl = worker.pictureUrl,
            ),
        )

    private fun accessToken(workerId: UUID, sessionId: UUID, now: Instant): String {
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(workerId.toString())
            .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl))
            .claim("roles", listOf(ROLE)).claim("sid", sessionId.toString()).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun clean(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)?.take(255)
    private fun minExpiry(idle: Instant, absolute: Instant?): Instant = if (absolute != null && absolute < idle) absolute else idle

    companion object { private const val ROLE = "WORKER" }
}
