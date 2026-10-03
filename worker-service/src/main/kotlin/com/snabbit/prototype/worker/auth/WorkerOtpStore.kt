package com.snabbit.prototype.worker.auth

import java.time.Duration

interface WorkerOtpStore {
    fun acquireCooldown(key: String, ttl: Duration): Boolean
    fun save(key: String, hash: String, ttl: Duration)
    fun hash(key: String): String?
    fun incrementAttempts(key: String, ttl: Duration): Long
    fun clear(key: String)
}
