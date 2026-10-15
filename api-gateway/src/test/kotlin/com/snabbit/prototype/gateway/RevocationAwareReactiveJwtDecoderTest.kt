package com.snabbit.prototype.gateway

import com.snabbit.prototype.platform.RevokedSessionKeys
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono

class RevocationAwareReactiveJwtDecoderTest {
    private val delegate = mockk<ReactiveJwtDecoder>()
    private val redis = mockk<ReactiveStringRedisTemplate>()
    private val decoder = RevocationAwareReactiveJwtDecoder(delegate, redis)

    @Test
    fun `customer and worker tokens require a session id`() {
        every { delegate.decode("token") } returns Mono.just(jwt("CUSTOMER", null))

        assertThrows(BadJwtException::class.java) { decoder.decode("token").block() }
    }

    @Test
    fun `revoked session is rejected immediately`() {
        val sessionId = UUID.randomUUID()
        every { delegate.decode("token") } returns Mono.just(jwt("WORKER", sessionId))
        every { redis.hasKey(RevokedSessionKeys.key(sessionId)) } returns Mono.just(true)

        assertThrows(BadJwtException::class.java) { decoder.decode("token").block() }
    }

    @Test
    fun `redis outage falls back to signed token expiry and staff remains unchanged`() {
        val sessionId = UUID.randomUUID()
        val customer = jwt("CUSTOMER", sessionId)
        every { delegate.decode("customer") } returns Mono.just(customer)
        every { redis.hasKey(RevokedSessionKeys.key(sessionId)) } returns Mono.error(IllegalStateException("redis unavailable"))
        assertEquals(customer, decoder.decode("customer").block())

        val staff = jwt("SUPPORT_AGENT", null)
        every { delegate.decode("staff") } returns Mono.just(staff)
        assertEquals(staff, decoder.decode("staff").block())
    }

    private fun jwt(role: String, sessionId: UUID?): Jwt {
        val now = Instant.parse("2026-10-15T04:00:00Z")
        return Jwt.withTokenValue("signed-access-token")
            .header("alg", "HS256").subject(UUID.randomUUID().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", listOf(role))
            .apply { sessionId?.let { claim("sid", it.toString()) } }
            .build()
    }
}
