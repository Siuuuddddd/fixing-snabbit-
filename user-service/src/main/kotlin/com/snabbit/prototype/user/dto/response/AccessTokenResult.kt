package com.snabbit.prototype.user.dto.response

data class AccessTokenResult(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)
