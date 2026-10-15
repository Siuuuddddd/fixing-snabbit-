package com.snabbit.prototype.worker.dto.response

data class WorkerSessionResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String,
    val user: WorkerSessionUser,
    override val requiresWorkerSelection: Boolean = false,
) : WorkerSignInResponse
