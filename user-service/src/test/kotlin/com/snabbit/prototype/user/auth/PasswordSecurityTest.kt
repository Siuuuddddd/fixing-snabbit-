package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.user.exception.WeakPassword
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordSecurityTest {
    @Test
    fun `common-password resource contains at least five hundred unique entries`() {
        val entries = javaClass.getResourceAsStream("/auth/common-passwords.txt")!!.bufferedReader().useLines { lines ->
            lines.map(String::trim).filter(String::isNotBlank).toSet()
        }
        assertTrue(entries.size >= 500)
    }

    @Test
    fun `policy rejects short common email and name values`() {
        val policy = PasswordPolicy(properties())
        assertReason("TOO_SHORT") { policy.validate("x", "person@example.test", "Test Customer") }
        assertReason("COMMON_PASSWORD") { policy.validate("password0", "person@example.test", "Test Customer") }
        assertReason("MATCHES_EMAIL") { policy.validate("person@example.test", "person@example.test", "Test Customer") }
        assertReason("MATCHES_NAME") { policy.validate("Test Customer", "person@example.test", "Test Customer") }
    }

    @Test
    fun `delegating encoder uses Argon2id and detects outdated parameters`() {
        val oldEncoder = PasswordEncoderConfiguration().passwordEncoder(properties().copy(argon2Iterations = 1))
        val currentEncoder = PasswordEncoderConfiguration().passwordEncoder(properties().copy(argon2Iterations = 2))
        val credential = OpaqueToken.generate()
        val encoded = oldEncoder.encode(credential)
        assertTrue(encoded.startsWith("{argon2id}"))
        assertTrue(currentEncoder.matches(credential, encoded))
        assertTrue(currentEncoder.upgradeEncoding(encoded))
    }

    private fun assertReason(reason: String, block: () -> Unit) {
        val error = assertThrows(WeakPassword::class.java, block)
        assertEquals(reason, error.reasonCode)
    }

    private fun properties() = EmailAuthProperties(codeHmacSecret = OpaqueToken.generate())
}
