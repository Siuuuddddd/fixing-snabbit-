package com.snabbit.prototype.user.staff

import com.snabbit.prototype.user.auth.AuthProperties
import com.snabbit.prototype.user.auth.OtpChallengeStore
import com.snabbit.prototype.user.auth.OtpDelivery
import com.snabbit.prototype.user.domain.enums.StaffStatus
import com.snabbit.prototype.user.dto.response.AccessTokenResult
import com.snabbit.prototype.user.dto.response.OtpRequestResult
import com.snabbit.prototype.user.exception.InvalidOtp
import com.snabbit.prototype.user.exception.OtpRateLimited
import com.snabbit.prototype.user.repository.StaffAccountRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
    private val delivery: OtpDelivery,
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun requestOtp(rawPhone: String): OtpRequestResult {
        val phone = normalize(rawPhone)
        val key = challengeKey(phone)
        if (!challenges.acquireRequestCooldown(key, properties.requestCooldown)) throw OtpRateLimited()
        val account = staff.findByPhoneE164(phone)
        if (account == null || account.status != StaffStatus.ACTIVE) {
            return OtpRequestResult(expiresInSeconds = properties.otpTtl.seconds, debugOtp = null)
        }
        val code = "%06d".format(random.nextInt(1_000_000))
        challenges.save(key, otpHash(phone, code), properties.otpTtl)
        delivery.deliver(phone, code)
        return OtpRequestResult(
            expiresInSeconds = properties.otpTtl.seconds,
            debugOtp = code.takeIf { properties.exposeCode },
        )
    }

    fun verifyOtp(rawPhone: String, code: String): AccessTokenResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidOtp()
        val key = challengeKey(phone)
        val attempts = challenges.incrementAttempts(key, properties.otpTtl)
        if (attempts > properties.maxAttempts) {
            challenges.clear(key)
            throw InvalidOtp()
        }
        val expected = challenges.codeHash(key) ?: throw InvalidOtp()
        if (!MessageDigest.isEqual(expected.toByteArray(), otpHash(phone, code).toByteArray())) throw InvalidOtp()
        val account = staff.findByPhoneE164(phone)?.takeIf { it.status == StaffStatus.ACTIVE } ?: throw InvalidOtp()
        challenges.clear(key)
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(account.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .claim("roles", listOf(account.role.name))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return AccessTokenResult(
            jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        if (!it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) {
            throw IllegalArgumentException("Phone number must be an Indian E.164 number such as +919800000001")
        }
    }

    private fun challengeKey(phone: String): String = "staff:" + HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(phone.toByteArray(StandardCharsets.UTF_8)),
    )

    private fun otpHash(phone: String, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(properties.otpPepper.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal("staff:$phone:$code".toByteArray(StandardCharsets.UTF_8)))
    }
}
