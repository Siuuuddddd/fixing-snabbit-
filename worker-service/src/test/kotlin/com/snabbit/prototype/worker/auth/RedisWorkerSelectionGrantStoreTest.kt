package com.snabbit.prototype.worker.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RedisWorkerSelectionGrantStoreTest {
    private val redis = mockk<StringRedisTemplate>()
    private val values = mockk<ValueOperations<String, String>>()
    private val ttl = Duration.ofMinutes(5)
    private val properties = WorkerAuthProperties(
        issuer = "https://auth.snabbit.test",
        jwtSecret = "worker-unit-test-secret-that-is-at-least-32-bytes",
        accessTokenTtl = Duration.ofMinutes(15),
        sharedSelectionTokenTtl = ttl,
    )

    @Test
    fun `stores only hashes and consumes a subject-bound grant once`() {
        val key = slot<String>()
        val value = slot<String>()
        val storedTtl = slot<Duration>()
        every { redis.opsForValue() } returns values
        every { values.set(capture(key), capture(value), capture(storedTtl)) } returns Unit
        val store = RedisWorkerSelectionGrantStore(redis, properties)
        val subject = "verified-google-subject"
        val workerId = UUID.randomUUID()

        val token = store.issue(subject, listOf(workerId))

        assertFalse(key.captured.contains(token))
        assertFalse(key.captured.contains(subject))
        assertFalse(value.captured.contains(subject))
        assertEquals(ttl, storedTtl.captured)

        every { values.getAndDelete(key.captured) } returns value.captured andThen null
        assertEquals(setOf(workerId), store.consume(token, subject))
        assertNull(store.consume(token, subject))
        verify(exactly = 2) { values.getAndDelete(key.captured) }
    }

    @Test
    fun `wrong subject consumes and rejects the grant`() {
        val key = slot<String>()
        val value = slot<String>()
        every { redis.opsForValue() } returns values
        every { values.set(capture(key), capture(value), any<Duration>()) } returns Unit
        val store = RedisWorkerSelectionGrantStore(redis, properties)
        val token = store.issue("subject-one", listOf(UUID.randomUUID()))
        every { values.getAndDelete(key.captured) } returns value.captured

        assertNull(store.consume(token, "subject-two"))
    }
}
