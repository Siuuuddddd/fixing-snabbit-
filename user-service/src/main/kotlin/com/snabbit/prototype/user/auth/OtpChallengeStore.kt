package com.snabbit.prototype.user.auth

import java.time.Duration

interface OtpChallengeStore {
    fun acquireRequestCooldown(key: String, ttl: Duration): Boolean
    fun releaseRequestCooldown(key: String) = Unit
    fun save(key: String, codeHash: String, ttl: Duration)
    fun codeHash(key: String): String?
    fun incrementAttempts(key: String, ttl: Duration): Long
    fun clear(key: String)
}
