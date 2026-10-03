package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.worker.domain.enums.WorkerStatus

import com.snabbit.prototype.platform.SmsGateway
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
@Profile("!local & !test")
class SmsWorkerOtpDelivery(private val sms: SmsGateway) : WorkerOtpDelivery {
    override fun deliver(phoneE164: String, code: String) {
        sms.send(phoneE164, "Your Snabbit worker verification code is $code. It expires in 5 minutes.")
    }
}
