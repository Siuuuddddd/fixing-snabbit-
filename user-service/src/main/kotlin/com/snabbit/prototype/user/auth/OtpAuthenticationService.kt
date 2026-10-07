package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationProviderRateLimited
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.response.AccessTokenResult
import com.snabbit.prototype.user.dto.response.OtpRequestResult
import com.snabbit.prototype.user.dto.response.OtpVerificationResult
import com.snabbit.prototype.user.exception.InvalidOtp
import com.snabbit.prototype.user.exception.OtpRateLimited
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.UUID
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OtpAuthenticationService(
    private val users: UserProfileRepository,
    private val challenges: OtpChallengeStore,
    private val verification: PhoneVerificationProvider,
    private val registrationGrants: RegistrationGrantStore,
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    fun requestOtp(rawPhone: String): OtpRequestResult {
        val phone = normalize(rawPhone)
        val key = phoneKey(phone)
        if (!challenges.acquireRequestCooldown(key, properties.requestCooldown)) throw OtpRateLimited()
        return try {
            val started = verification.start(phone)
            OtpRequestResult(status = "OTP_SENT", expiresInSeconds = started.expiresInSeconds, debugOtp = started.debugCode)
        } catch (error: VerificationProviderRateLimited) {
            throw OtpRateLimited()
        } catch (error: RuntimeException) {
            challenges.releaseRequestCooldown(key)
            throw error
        }
    }

    fun verifyOtp(rawPhone: String, code: String): OtpVerificationResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidOtp()
        val key = phoneKey(phone)
        if (challenges.incrementAttempts(key, properties.otpTtl) > properties.maxAttempts) {
            verification.clear(phone); challenges.clear(key); throw InvalidOtp()
        }
        when (verification.check(phone, code)) {
            VerificationStatus.APPROVED -> Unit
            else -> throw InvalidOtp()
        }
        challenges.clear(key)
        val user = users.findByPhoneE164(phone)
        if (user == null) return OtpVerificationResult(
            status = "REGISTRATION_REQUIRED",
            registrationToken = registrationGrants.issue(phone, properties.otpTtl),
        )
        if (user.status != UserStatus.ACTIVE) throw IllegalStateException("This customer account is unavailable")
        return authenticated(user)
    }

    @Transactional
    fun register(rawPhone: String, rawName: String, grant: String): OtpVerificationResult {
        val phone = normalize(rawPhone)
        if (registrationGrants.consume(grant) != phone) throw InvalidOtp()
        val name = rawName.trim().also { require(it.length in 2..60) { "Name must contain 2 to 60 characters" } }
        val user = users.findByPhoneE164(phone) ?: users.save(UserProfile(
            id = UUID.randomUUID(), phoneE164 = phone, displayName = name,
            status = UserStatus.ACTIVE, plusActive = false, plusExpiresAt = null,
            createdAt = clock.instant(), updatedAt = clock.instant(),
        ))
        if (user.status != UserStatus.ACTIVE) throw IllegalStateException("This customer account is unavailable")
        return authenticated(user)
    }

    private fun authenticated(user: UserProfile): OtpVerificationResult {
        val token = issue(user.id)
        return OtpVerificationResult("AUTHENTICATED", token.accessToken, token.tokenType, token.expiresInSeconds)
    }

    private fun issue(id: UUID): AccessTokenResult {
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(id.toString())
            .issuedAt(issuedAt).expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .claim("roles", listOf("CUSTOMER")).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessTokenResult(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresInSeconds = properties.accessTokenTtl.seconds)
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        require(it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) { "Enter a valid Indian mobile number" }
    }

    private fun phoneKey(phone: String) = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(phone.toByteArray(StandardCharsets.UTF_8)),
    )
}
