package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationProviderRateLimited
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.dto.response.WorkerAccessTokenResult
import com.snabbit.prototype.worker.dto.response.WorkerOtpRequestResult
import com.snabbit.prototype.worker.exception.InvalidWorkerOtp
import com.snabbit.prototype.worker.exception.WorkerOtpRateLimited
import com.snabbit.prototype.worker.exception.WorkerProfileRequired
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
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
class WorkerAuthenticationService(
    private val workers: WorkerProfileRepository,
    private val store: WorkerOtpStore,
    private val verification: PhoneVerificationProvider,
    private val jwtEncoder: JwtEncoder,
    private val properties: WorkerAuthProperties,
    private val clock: Clock,
) {
    fun requestOtp(rawPhone: String): WorkerOtpRequestResult {
        val phone = normalize(rawPhone)
        val key = phoneKey(phone)
        if (!store.acquireCooldown(key, properties.requestCooldown)) throw WorkerOtpRateLimited()
        return try {
            val started = verification.start(phone)
            WorkerOtpRequestResult(status = "OTP_SENT", expiresInSeconds = started.expiresInSeconds, debugOtp = started.debugCode)
        } catch (error: VerificationProviderRateLimited) {
            throw WorkerOtpRateLimited()
        } catch (error: RuntimeException) {
            store.releaseCooldown(key); throw error
        }
    }

    fun verifyOtp(rawPhone: String, code: String): WorkerAccessTokenResult {
        val phone = normalize(rawPhone)
        if (!code.matches(Regex("^[0-9]{6}$"))) throw InvalidWorkerOtp()
        val key = phoneKey(phone)
        if (store.incrementAttempts(key, properties.otpTtl) > properties.maxAttempts) {
            verification.clear(phone); store.clear(key); throw InvalidWorkerOtp()
        }
        if (verification.check(phone, code) != VerificationStatus.APPROVED) throw InvalidWorkerOtp()
        store.clear(key)
        val worker = workers.findByPhoneE164(phone)?.takeIf { it.status == WorkerStatus.ACTIVE }
            ?: throw WorkerProfileRequired()
        val issuedAt = clock.instant()
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(worker.id.toString())
            .issuedAt(issuedAt).expiresAt(issuedAt.plus(properties.accessTokenTtl))
            .claim("roles", listOf("WORKER")).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return WorkerAccessTokenResult(
            jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }

    private fun normalize(rawPhone: String): String = rawPhone.trim().also {
        require(it.matches(Regex("^\\+91[6-9][0-9]{9}$"))) { "Enter a valid Indian mobile number" }
    }

    private fun phoneKey(phone: String) = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(phone.toByteArray(StandardCharsets.UTF_8)),
    )
}
