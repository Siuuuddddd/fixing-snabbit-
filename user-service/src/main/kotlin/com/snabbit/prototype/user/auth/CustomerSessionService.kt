package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.domain.CustomerRotatedRefreshToken
import com.snabbit.prototype.user.domain.CustomerSession
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.dto.response.CustomerSessionUser
import com.snabbit.prototype.user.exception.RefreshInProgress
import com.snabbit.prototype.user.exception.SessionRevoked
import com.snabbit.prototype.user.repository.CustomerRotatedRefreshTokenRepository
import com.snabbit.prototype.user.repository.CustomerSessionRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.time.Duration
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
class CustomerSessionService(
    private val sessions: CustomerSessionRepository,
    private val rotatedTokens: CustomerRotatedRefreshTokenRepository,
    private val users: UserProfileRepository,
    private val revocations: CustomerSessionRevocationStore,
    private val rateLimiter: CustomerAuthRateLimiter,
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    @Transactional
    fun create(user: UserProfile, deviceId: String?, deviceName: String?, isNewUser: Boolean): CustomerSessionResponse {
        val now = clock.instant()
        enforceCap(user.id, now)
        val rawRefresh = OpaqueToken.generate()
        val session = CustomerSession(
            id = UUID.randomUUID(), userId = user.id, role = ROLE,
            refreshTokenHash = OpaqueToken.hash(rawRefresh), tokenFamilyId = UUID.randomUUID(),
            deviceId = clean(deviceId), deviceName = clean(deviceName), sharedLogin = false,
            createdAt = now, lastUsedAt = now, idleExpiresAt = now.plus(properties.refreshIdleTtl),
            absoluteExpiresAt = properties.refreshAbsoluteTtl?.let(now::plus),
        )
        sessions.save(session)
        return response(session, user, rawRefresh, isNewUser, now)
    }

    @Transactional(noRollbackFor = [SessionRevoked::class, RefreshInProgress::class])
    fun refresh(rawRefresh: String): CustomerSessionResponse {
        val hash = OpaqueToken.hash(rawRefresh)
        val session = sessions.findByRefreshHashForUpdate(hash) ?: handleRotatedOrUnknown(hash)
        rateLimiter.check("refresh", session.userId.toString())
        val now = clock.instant()
        val user = users.findForUpdate(session.userId) ?: return revokeAndThrow(session, now, "ACCOUNT_MISSING")
        ensureUsable(session, user, now)
        rotatedTokens.save(CustomerRotatedRefreshToken(UUID.randomUUID(), session.id, hash, now))
        val replacement = OpaqueToken.generate()
        session.refreshTokenHash = OpaqueToken.hash(replacement)
        session.lastUsedAt = now
        session.idleExpiresAt = minExpiry(now.plus(properties.refreshIdleTtl), session.absoluteExpiresAt)
        return response(session, user, replacement, false, now)
    }

    @Transactional
    fun logout(userId: UUID, sessionId: UUID) {
        val now = clock.instant()
        val session = sessions.findForUpdate(sessionId) ?: return
        if (session.userId != userId) throw SessionRevoked()
        revoke(session, now, "LOGOUT")
    }

    @Transactional
    fun logoutAll(userId: UUID, reason: String = "LOGOUT_ALL") = invalidateAllSessions(userId, reason)

    @Transactional
    fun rotateAfterCredentialChange(userId: UUID, sessionId: UUID): CustomerSessionResponse {
        val now = clock.instant()
        val user = users.findForUpdate(userId) ?: throw SessionRevoked()
        val current = sessions.findForUpdate(sessionId) ?: throw SessionRevoked()
        if (current.userId != userId) throw SessionRevoked()
        ensureUsable(current, user, now)
        sessions.findActiveByUserId(userId).filter { it.id != sessionId }.forEach {
            revoke(it, now, "PASSWORD_CHANGED")
        }
        rotatedTokens.save(CustomerRotatedRefreshToken(UUID.randomUUID(), current.id, current.refreshTokenHash, now))
        val replacement = OpaqueToken.generate()
        current.refreshTokenHash = OpaqueToken.hash(replacement)
        current.lastUsedAt = now
        current.idleExpiresAt = minExpiry(now.plus(properties.refreshIdleTtl), current.absoluteExpiresAt)
        return response(current, user, replacement, false, now)
    }

    @Transactional(readOnly = true)
    fun currentUser(userId: UUID): CustomerSessionUser = users.findById(userId)
        .map { userView(it, false) }
        .orElseThrow { SessionRevoked() }

    @Transactional
    fun invalidateAllSessions(userId: UUID, reason: String) {
        val now = clock.instant()
        val user = users.findForUpdate(userId) ?: throw SessionRevoked()
        user.sessionsValidAfter = now
        user.updatedAt = now
        sessions.findActiveByUserId(userId).forEach { revoke(it, now, reason) }
    }

    private fun handleRotatedOrUnknown(hash: String): CustomerSession {
        val used = rotatedTokens.findByTokenHash(hash) ?: throw SessionRevoked()
        val session = sessions.findForUpdate(used.sessionId) ?: throw SessionRevoked()
        val now = clock.instant()
        if (now.isBefore(used.rotatedAt.plus(properties.refreshGraceWindow))) throw RefreshInProgress()
        sessions.findByTokenFamilyId(session.tokenFamilyId).forEach { revoke(it, now, "REFRESH_REUSE") }
        throw SessionRevoked()
    }

    private fun ensureUsable(session: CustomerSession, user: UserProfile, now: Instant) {
        if (session.revokedAt != null || !now.isBefore(session.idleExpiresAt) ||
            session.absoluteExpiresAt?.let { !now.isBefore(it) } == true ||
            session.createdAt.isBefore(user.sessionsValidAfter)
        ) revokeAndThrow(session, now, "SESSION_EXPIRED")
    }

    private fun revokeAndThrow(session: CustomerSession, now: Instant, reason: String): Nothing {
        revoke(session, now, reason)
        throw SessionRevoked()
    }

    private fun enforceCap(userId: UUID, now: Instant) {
        val active = sessions.findActiveByUserId(userId)
        val evictCount = active.size - properties.maxActiveSessions + 1
        if (evictCount > 0) active.take(evictCount).forEach { revoke(it, now, "SESSION_LIMIT") }
    }

    private fun revoke(session: CustomerSession, now: Instant, reason: String) {
        session.revoke(now, reason)
        revocations.revoke(session.id, properties.accessTokenTtl)
    }

    private fun response(
        session: CustomerSession,
        user: UserProfile,
        refreshToken: String,
        isNewUser: Boolean,
        now: Instant,
    ): CustomerSessionResponse = CustomerSessionResponse(
        accessToken = accessToken(user.id, session.id, now),
        expiresIn = properties.accessTokenTtl.seconds,
        refreshToken = refreshToken,
        user = userView(user, isNewUser),
    )

    private fun userView(user: UserProfile, isNewUser: Boolean): CustomerSessionUser = CustomerSessionUser(
        id = user.id,
        name = user.displayName,
        email = requireNotNull(user.email),
        pictureUrl = user.pictureUrl,
        isNewUser = isNewUser,
        emailVerified = user.emailVerified,
        hasPassword = user.passwordHash != null,
        authProviders = user.authProviders.map { it.name }.toSortedSet(),
    )

    private fun accessToken(userId: UUID, sessionId: UUID, now: Instant): String {
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(userId.toString())
            .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl))
            .claim("roles", listOf(ROLE)).claim("sid", sessionId.toString()).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun clean(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)?.take(255)
    private fun minExpiry(idle: Instant, absolute: Instant?): Instant = if (absolute != null && absolute < idle) absolute else idle

    companion object { private const val ROLE = "CUSTOMER" }
}
