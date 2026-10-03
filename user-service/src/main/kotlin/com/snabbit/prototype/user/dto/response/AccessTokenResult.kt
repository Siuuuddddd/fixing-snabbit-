package com.snabbit.prototype.user.dto.response

import com.snabbit.prototype.user.domain.enums.UserStatus

import com.snabbit.prototype.user.repository.UserProfileRepository
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

data class AccessTokenResult(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)
