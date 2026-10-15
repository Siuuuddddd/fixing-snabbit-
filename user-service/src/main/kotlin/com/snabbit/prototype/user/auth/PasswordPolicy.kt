package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.exception.WeakPassword
import java.nio.charset.StandardCharsets
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils

@Component
class PasswordPolicy(private val properties: EmailAuthProperties) {
    private val common = ClassPathResource("auth/common-passwords.txt").inputStream.bufferedReader().useLines { lines ->
        lines.map(String::trim).filter(String::isNotEmpty).map(String::lowercase).toSet()
    }

    fun validate(password: String, email: String, name: String?) {
        when {
            password.length < properties.passwordMinLength -> throw WeakPassword("TOO_SHORT")
            password.length > properties.passwordMaxLength -> throw WeakPassword("TOO_LONG")
            !bouncyCastleAvailable() && password.toByteArray(StandardCharsets.UTF_8).size > 72 ->
                throw WeakPassword("BCRYPT_MAX_BYTES")
            password.equals(email, ignoreCase = true) -> throw WeakPassword("MATCHES_EMAIL")
            !name.isNullOrBlank() && password.equals(name.trim(), ignoreCase = true) -> throw WeakPassword("MATCHES_NAME")
            common.contains(password.lowercase()) -> throw WeakPassword("COMMON_PASSWORD")
        }
    }

    private fun bouncyCastleAvailable(): Boolean = ClassUtils.isPresent(
        "org.bouncycastle.crypto.generators.Argon2BytesGenerator",
        PasswordPolicy::class.java.classLoader,
    )
}
