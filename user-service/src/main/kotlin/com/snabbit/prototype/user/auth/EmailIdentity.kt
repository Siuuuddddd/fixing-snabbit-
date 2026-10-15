package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.exception.ValidationRejected

object EmailIdentity {
    private val format = Regex("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        val normalized = raw.trim().lowercase()
        if (normalized.isEmpty() || normalized.length > 254 || !format.matches(normalized)) {
            throw ValidationRejected(mapOf("email" to "INVALID_EMAIL"))
        }
        return normalized
    }
}
