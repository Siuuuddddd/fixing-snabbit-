package com.snabbit.prototype.user.staff

import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.user.auth.AuthProperties
import com.snabbit.prototype.user.auth.OtpChallengeStore
import com.snabbit.prototype.user.domain.enums.StaffStatus
import com.snabbit.prototype.user.dto.response.AccessTokenResult
import com.snabbit.prototype.user.dto.response.OtpRequestResult
import com.snabbit.prototype.user.exception.InvalidOtp
import com.snabbit.prototype.user.exception.OtpRateLimited
import com.snabbit.prototype.user.repository.StaffAccountRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service

@Service
class StaffAuthenticationService(
    private val staff: StaffAccountRepository,
    private val challenges: OtpChallengeStore,
    private val verification: PhoneVerificationProvider,
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    fun requestOtp(rawPhone: String): OtpRequestResult {
        val phone = normalize(rawPhone)
        val key = challengeKey(phone)
        if (!challenges.acquireRequestCooldown(key, properties.requestCooldown)) throw OtpRateLimited()
        return try {
            verification.start(phone).let { OtpRequestResult("OTP_SENT", it.expiresInSeconds, it.debugCode) }
        } catch (error: RuntimeException) {
            challenges.releaseRequestCooldown(key); throw error
        }
    }

    fun verifyOtp(rawPhone: String, code: String): AccessTokenResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidOtp()
        val key = challengeKey(phone)
        if (challenges.incrementAttempts(key, properties.otpTtl) > properties.maxAttempts ||
            verification.check(phone, code) != VerificationStatus.APPROVED) throw InvalidOtp()
        val account = staff.findByPhoneE164(phone)?.takeIf { it.status == StaffStatus.ACTIVE } ?: throw InvalidOtp()
        challenges.clear(key)
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(account.id.toString())
            .issuedAt(issuedAt).expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .claim("roles", listOf(account.role.name)).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessTokenResult(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresInSeconds = properties.accessTokenTtl.seconds)
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        require(it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) { "Enter a valid Indian mobile number" }
    }

    private fun challengeKey(phone: String) = "staff:" + HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(phone.toByteArray(StandardCharsets.UTF_8)),
    )
}
