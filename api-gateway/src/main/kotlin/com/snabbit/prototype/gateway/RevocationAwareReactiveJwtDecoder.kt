package com.snabbit.prototype.gateway

import com.snabbit.prototype.platform.RevokedSessionKeys
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

class RevocationAwareReactiveJwtDecoder(
    private val delegate: ReactiveJwtDecoder,
    private val redis: ReactiveStringRedisTemplate,
) : ReactiveJwtDecoder {
    override fun decode(token: String): Mono<Jwt> = delegate.decode(token).flatMap { jwt ->
        val roles = jwt.getClaimAsStringList("roles").orEmpty()
        if (roles.none { it == "CUSTOMER" || it == "WORKER" }) return@flatMap Mono.just(jwt)
        val sessionId = runCatching { UUID.fromString(jwt.getClaimAsString("sid")) }.getOrNull()
            ?: return@flatMap Mono.error(BadJwtException("Session claim is required"))
        redis.hasKey(RevokedSessionKeys.key(sessionId))
            .flatMap { revoked -> if (revoked) Mono.error(BadJwtException("Session is revoked")) else Mono.just(jwt) }
            .onErrorResume { error ->
                if (error is BadJwtException) Mono.error(error) else {
                    logger.warn("Session revocation lookup unavailable; access token expiry remains enforced", error)
                    Mono.just(jwt)
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RevocationAwareReactiveJwtDecoder::class.java)
    }
}
