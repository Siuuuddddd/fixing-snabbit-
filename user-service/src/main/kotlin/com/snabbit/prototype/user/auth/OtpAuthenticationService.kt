package com.snabbit.prototype.user.auth

import com.snabbit.prototype.user.domain.UserStatus
import com.snabbit.prototype.user.persistence.UserProfileRepository
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class OtpRequestResult(
    val status: String = "OTP_SENT",
    val expiresInSeconds: Long,
    val debugOtp: String?,
)

data class AccessTokenResult(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)

class OtpRateLimited : RuntimeException("Please wait before requesting another OTP")
class InvalidOtp : RuntimeException("The OTP is invalid or expired")

@Service
class OtpAuthenticationService(
    private val users: UserProfileRepository,
    private val challenges: OtpChallengeStore,
    private val delivery: OtpDelivery,
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun requestOtp(rawPhone: String): OtpRequestResult {
        val phone = normalize(rawPhone)
        val keySuffix = phoneKey(phone)
        if (!challenges.acquireRequestCooldown(keySuffix, properties.requestCooldown)) throw OtpRateLimited()

        val user = users.findByPhoneE164(phone)
        if (user == null || user.status != UserStatus.ACTIVE) {
            return OtpRequestResult(expiresInSeconds = properties.otpTtl.seconds, debugOtp = null)
        }

        val code = "%06d".format(random.nextInt(1_000_000))
        challenges.save(keySuffix, otpHash(phone, code), properties.otpTtl)
        delivery.deliver(phone, code)
        return OtpRequestResult(
            expiresInSeconds = properties.otpTtl.seconds,
            debugOtp = code.takeIf { properties.exposeCode },
        )
    }

    fun verifyOtp(rawPhone: String, code: String): AccessTokenResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidOtp()
        val keySuffix = phoneKey(phone)
        val attempts = challenges.incrementAttempts(keySuffix, properties.otpTtl)
        if (attempts > properties.maxAttempts) {
            clearChallenge(keySuffix)
            throw InvalidOtp()
        }

        val expected = challenges.codeHash(keySuffix) ?: throw InvalidOtp()
        val actual = otpHash(phone, code)
        if (!MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) throw InvalidOtp()
        val user = users.findByPhoneE164(phone)?.takeIf { it.status == UserStatus.ACTIVE } ?: throw InvalidOtp()

        clearChallenge(keySuffix)
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(properties.accessTokenTtl)
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(user.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", listOf("CUSTOMER"))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessTokenResult(
            accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    private fun clearChallenge(keySuffix: String) {
        challenges.clear(keySuffix)
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        if (!it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) {
            throw IllegalArgumentException("Phone number must be an Indian E.164 number such as +919876543210")
        }
    }

    private fun phoneKey(phone: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(phone.toByteArray(StandardCharsets.UTF_8)),
    )

    private fun otpHash(phone: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.otpPepper.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal("$phone:$code".toByteArray(StandardCharsets.UTF_8)))
    }
}
