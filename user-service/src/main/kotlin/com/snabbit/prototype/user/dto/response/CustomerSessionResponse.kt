package com.snabbit.prototype.user.dto.response

data class CustomerSessionResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String,
    val user: CustomerSessionUser,
)
