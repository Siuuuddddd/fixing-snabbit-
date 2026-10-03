package com.snabbit.prototype.worker.dto.response

data class WorkerAccessTokenResult(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)
