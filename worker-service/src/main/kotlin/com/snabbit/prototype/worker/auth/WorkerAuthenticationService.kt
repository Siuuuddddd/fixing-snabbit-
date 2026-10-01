package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.worker.domain.WorkerStatus
import com.snabbit.prototype.worker.persistence.WorkerProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class WorkerOtpRequestResult(
    val status: String = "OTP_SENT",
    val expiresInSeconds: Long,
    val debugOtp: String?,
)

data class WorkerAccessTokenResult(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)

class WorkerOtpRateLimited : RuntimeException("Please wait before requesting another OTP")
class InvalidWorkerOtp : RuntimeException("The OTP is invalid or expired")

interface WorkerOtpDelivery {
    fun deliver(phoneE164: String, code: String)
}

@Component
class LoggingWorkerOtpDelivery : WorkerOtpDelivery {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun deliver(phoneE164: String, code: String) {
        logger.info("Prototype worker OTP generated for {}: {}", phoneE164.takeLast(4).padStart(phoneE164.length, '*'), code)
    }
}

@Service
class WorkerAuthenticationService(
    private val workers: WorkerProfileRepository,
    private val store: WorkerOtpStore,
    private val delivery: WorkerOtpDelivery,
    private val jwtEncoder: JwtEncoder,
    private val properties: WorkerAuthProperties,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun requestOtp(rawPhone: String): WorkerOtpRequestResult {
        val phone = normalize(rawPhone)
        val key = phoneKey(phone)
        if (!store.acquireCooldown(key, properties.requestCooldown)) throw WorkerOtpRateLimited()
        val worker = workers.findByPhoneE164(phone)
        if (worker == null || worker.status != WorkerStatus.ACTIVE) {
            return WorkerOtpRequestResult(expiresInSeconds = properties.otpTtl.seconds, debugOtp = null)
        }
        val code = "%06d".format(random.nextInt(1_000_000))
        store.save(key, otpHash(phone, code), properties.otpTtl)
        delivery.deliver(phone, code)
        return WorkerOtpRequestResult(
            expiresInSeconds = properties.otpTtl.seconds,
            debugOtp = code.takeIf { properties.exposeCode },
        )
    }

    fun verifyOtp(rawPhone: String, code: String): WorkerAccessTokenResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidWorkerOtp()
        val key = phoneKey(phone)
        val attempts = store.incrementAttempts(key, properties.otpTtl)
        if (attempts > properties.maxAttempts) {
            store.clear(key)
            throw InvalidWorkerOtp()
        }
        val expected = store.hash(key) ?: throw InvalidWorkerOtp()
        if (!MessageDigest.isEqual(expected.toByteArray(), otpHash(phone, code).toByteArray())) throw InvalidWorkerOtp()
        val worker = workers.findByPhoneE164(phone)?.takeIf { it.status == WorkerStatus.ACTIVE }
            ?: throw InvalidWorkerOtp()
        store.clear(key)
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(worker.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .claim("roles", listOf("WORKER"))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return WorkerAccessTokenResult(
            jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        if (!it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) {
            throw IllegalArgumentException("Phone number must be an Indian E.164 number such as +919900000001")
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
