package com.snabbit.prototype.user.auth

import java.time.Duration

interface OtpChallengeStore {
    fun acquireRequestCooldown(key: String, ttl: Duration): Boolean
    fun releaseRequestCooldown(key: String) = Unit
    fun incrementAttempts(key: String, ttl: Duration): Long
    fun clear(key: String)
}
