package com.snabbit.prototype.worker.auth

import java.time.Duration

interface WorkerOtpStore {
    fun acquireCooldown(key: String, ttl: Duration): Boolean
    fun releaseCooldown(key: String) = Unit
    fun incrementAttempts(key: String, ttl: Duration): Long
    fun clear(key: String)
}
