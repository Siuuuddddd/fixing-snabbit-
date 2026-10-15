package com.snabbit.prototype.platform

import java.time.Clock
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

class NimbusGoogleIdentityVerifier internal constructor(
    private val properties: GoogleIdentityProperties,
    private val clock: Clock,
    private val decoder: JwtDecoder,
) : GoogleIdentityVerifier {
    constructor(properties: GoogleIdentityProperties, clock: Clock) : this(
        properties,
        clock,
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build(),
    )

    init {
        require(properties.verifier.equals("google", ignoreCase = true)) {
            "auth.google.verifier must be google"
        }
        require(properties.issuers.isNotEmpty()) { "At least one Google token issuer is required" }
    }

    override fun verify(
        idToken: String,
        allowedClientIds: Set<String>,
        expectedNonce: String?,
    ): GoogleIdentity {
        if (idToken.isBlank() || allowedClientIds.isEmpty()) invalid()
        val jwt = decode(idToken)
        if (jwt.issuer?.toString() !in properties.issuers) invalid()
        if (jwt.expiresAt?.isAfter(clock.instant()) != true) invalid()
        if (jwt.audience.none(allowedClientIds::contains)) invalid()

        val subject = jwt.subject?.trim().orEmpty().takeIf(String::isNotEmpty) ?: invalid()
        val email = jwt.getClaimAsString("email")?.trim().orEmpty().takeIf(String::isNotEmpty) ?: invalid()
        if (!jwt.emailVerified()) {
            throw GoogleIdentityVerificationException(GoogleIdentityFailure.EMAIL_NOT_VERIFIED)
        }
        if (expectedNonce != null && jwt.getClaimAsString("nonce") != expectedNonce) {
            throw GoogleIdentityVerificationException(GoogleIdentityFailure.NONCE_MISMATCH)
        }
        return GoogleIdentity(
            subject = subject,
            email = email,
            displayName = jwt.getClaimAsString("name")?.trim()?.takeIf(String::isNotEmpty),
            pictureUrl = jwt.getClaimAsString("picture")?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun decode(idToken: String): Jwt = try {
        decoder.decode(idToken)
    } catch (_: JwtException) {
        invalid()
    } catch (_: RuntimeException) {
        invalid()
    }

    private fun Jwt.emailVerified(): Boolean = when (val claim = claims["email_verified"]) {
        is Boolean -> claim
        is String -> claim.equals("true", ignoreCase = true)
        else -> false
    }

    private fun invalid(): Nothing = throw GoogleIdentityVerificationException(GoogleIdentityFailure.INVALID_TOKEN)
}
