package com.snabbit.prototype.user.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.util.ClassUtils

@Configuration
class PasswordEncoderConfiguration {
    @Bean
    fun passwordEncoder(properties: EmailAuthProperties): PasswordEncoder {
        val bcrypt = BCryptPasswordEncoder(properties.bcryptStrength)
        val bouncyCastleAvailable = ClassUtils.isPresent(
            "org.bouncycastle.crypto.generators.Argon2BytesGenerator",
            PasswordEncoderConfiguration::class.java.classLoader,
        )
        if (!bouncyCastleAvailable) return DelegatingPasswordEncoder("bcrypt", mapOf("bcrypt" to bcrypt))
        val argon2 = Argon2PasswordEncoder(
            properties.argon2SaltLength, properties.argon2HashLength, properties.argon2Parallelism,
            properties.argon2MemoryKb, properties.argon2Iterations,
        )
        return DelegatingPasswordEncoder("argon2id", mapOf("argon2id" to argon2, "bcrypt" to bcrypt))
    }
}
