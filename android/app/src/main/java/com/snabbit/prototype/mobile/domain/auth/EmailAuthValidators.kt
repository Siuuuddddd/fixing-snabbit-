package com.snabbit.prototype.mobile.domain.auth

import java.security.MessageDigest

object EmailValidator {
    private val pattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun normalize(value: String): String = value.trim().lowercase()
    fun isValid(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.length in 3..254 && pattern.matches(normalized)
    }
}

object NameValidator {
    fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
    fun isValid(value: String): Boolean = normalize(value).length in 2..120
}

enum class PasswordRule {
    LENGTH,
    DIFFERS_FROM_EMAIL,
    DIFFERS_FROM_NAME,
    NOT_COMMON,
}

data class PasswordValidation(val failedRules: Set<PasswordRule>) {
    val isValid: Boolean get() = failedRules.isEmpty()
    fun passes(rule: PasswordRule): Boolean = rule !in failedRules
}

class PasswordRules(
    private val commonPasswordHashes: Set<String> = DEFAULT_COMMON_PASSWORD_HASHES,
) {
    fun validate(password: String, email: String = "", name: String = ""): PasswordValidation {
        val normalizedEmail = EmailValidator.normalize(email)
        val normalizedName = NameValidator.normalize(name)
        val normalizedPassword = password.trim().lowercase()
        return PasswordValidation(buildSet {
            if (password.length !in MIN_LENGTH..MAX_LENGTH) add(PasswordRule.LENGTH)
            if (normalizedEmail.isNotEmpty() && normalizedPassword == normalizedEmail) add(PasswordRule.DIFFERS_FROM_EMAIL)
            if (normalizedName.isNotEmpty() && normalizedPassword == normalizedName.lowercase()) add(PasswordRule.DIFFERS_FROM_NAME)
            if (sha256(normalizedPassword) in commonPasswordHashes) add(PasswordRule.NOT_COMMON)
        })
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 128

        // SHA-256 values avoid embedding usable passwords in the app while still providing quick local feedback.
        private val DEFAULT_COMMON_PASSWORD_HASHES = setOf(
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            "ef92b778bafe771e89245b89ecbc08a44a4e166c06665a081f24f87f498a105f",
            "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
            "f52fbd32b2b3b86ff88ef6c490628285f482af15ddcb29541f94bcf526a3f6c7",
            "6c7cf3e8f7f68f782370cce875ea36585c6e16848108a797cdf5b6c939ca0d23",
        )

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

