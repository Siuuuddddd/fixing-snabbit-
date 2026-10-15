package com.snabbit.prototype.platform

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder

class NimbusGoogleIdentityVerifierTest {
    private val now = Instant.parse("2026-10-15T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = GoogleIdentityProperties()
    private val allowedAudiences = setOf("configured-client-id")
    private val verifiedEmail = listOf("verified-user", "test.invalid")
        .joinToString(separator = Char(64).toString())

    @Test
    fun `accepts a valid verified Google identity`() {
        val identity = verifier(jwt()).verify("signed-token", allowedAudiences, "nonce-value")

        assertEquals("google-subject", identity.subject)
        assertEquals(verifiedEmail, identity.email)
        assertEquals("Verified User", identity.displayName)
        assertEquals("https://images.test.invalid/avatar", identity.pictureUrl)
    }

    @Test
    fun `rejects wrong audience issuer expiry signature and missing subject`() {
        val invalidTokens = listOf(
            jwt(audience = listOf("other-client-id")),
            jwt(issuer = "https://issuer.test.invalid"),
            jwt(expiresAt = now.minusSeconds(1)),
            jwt(subject = null),
        )
        invalidTokens.forEach { token ->
            val error = assertThrows(GoogleIdentityVerificationException::class.java) {
                verifier(token).verify("signed-token", allowedAudiences)
            }
            assertEquals(GoogleIdentityFailure.INVALID_TOKEN, error.failure)
        }

        val badSignature = NimbusGoogleIdentityVerifier(properties, clock, JwtDecoder {
            throw BadJwtException("signature rejected")
        })
        val error = assertThrows(GoogleIdentityVerificationException::class.java) {
            badSignature.verify("bad-signature", allowedAudiences)
        }
        assertEquals(GoogleIdentityFailure.INVALID_TOKEN, error.failure)
    }

    @Test
    fun `rejects unverified email and nonce mismatch`() {
        val unverified = assertThrows(GoogleIdentityVerificationException::class.java) {
            verifier(jwt(emailVerified = false)).verify("signed-token", allowedAudiences)
        }
        assertEquals(GoogleIdentityFailure.EMAIL_NOT_VERIFIED, unverified.failure)

        val nonceMismatch = assertThrows(GoogleIdentityVerificationException::class.java) {
            verifier(jwt()).verify("signed-token", allowedAudiences, "different-nonce")
        }
        assertEquals(GoogleIdentityFailure.NONCE_MISMATCH, nonceMismatch.failure)
    }

    @Test
    fun `remote Google keys are cached and an unknown key id triggers rotation refresh`() {
        val firstKey = RSAKeyGenerator(2048).keyID("first-key").generate()
        val secondKey = RSAKeyGenerator(2048).keyID("second-key").generate()
        val activeKeys = AtomicReference(listOf(firstKey))
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/certs") { exchange ->
                requests.incrementAndGet()
                val response = JWKSet(activeKeys.get().map(RSAKey::toPublicJWK)).toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        try {
            val rotatingProperties = properties.copy(
                jwkSetUri = "http://127.0.0.1:${server.address.port}/certs",
            )
            val verifier = NimbusGoogleIdentityVerifier(rotatingProperties, Clock.systemUTC())
            val firstToken = signedToken(firstKey)
            verifier.verify(firstToken, allowedAudiences)
            verifier.verify(firstToken, allowedAudiences)
            assertEquals(1, requests.get(), "the known signing key should be served from the decoder cache")

            activeKeys.set(listOf(firstKey, secondKey))
            verifier.verify(signedToken(secondKey), allowedAudiences)
            assertEquals(2, requests.get(), "an unknown key id should refresh the remote key set")
        } finally {
            server.stop(0)
        }
    }

    private fun verifier(jwt: Jwt): NimbusGoogleIdentityVerifier = NimbusGoogleIdentityVerifier(
        properties,
        clock,
        JwtDecoder { jwt },
    )

    private fun jwt(
        issuer: String = "https://accounts.google.com",
        audience: List<String> = allowedAudiences.toList(),
        expiresAt: Instant = now.plusSeconds(300),
        subject: String? = "google-subject",
        emailVerified: Boolean = true,
    ): Jwt {
        val builder = Jwt.withTokenValue("signed-token")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(now.minusSeconds(600))
            .expiresAt(expiresAt)
            .claim("email", verifiedEmail)
            .claim("email_verified", emailVerified)
            .claim("name", "Verified User")
            .claim("picture", "https://images.test.invalid/avatar")
            .claim("nonce", "nonce-value")
        if (subject != null) builder.subject(subject)
        return builder.build()
    }

    private fun signedToken(key: RSAKey): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .subject("google-subject")
            .audience(allowedAudiences.first())
            .issueTime(Date.from(Instant.now().minusSeconds(1)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("email", verifiedEmail)
            .claim("email_verified", true)
            .build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
            claims,
        ).apply { sign(RSASSASigner(key.toPrivateKey())) }.serialize()
    }
}
