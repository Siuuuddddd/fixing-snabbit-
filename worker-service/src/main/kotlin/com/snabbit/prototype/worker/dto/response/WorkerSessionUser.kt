package com.snabbit.prototype.worker.dto.response

import java.util.UUID

data class WorkerSessionUser(
    val id: UUID,
    val name: String,
    val email: String,
    val pictureUrl: String?,
    val isNewUser: Boolean = false,
    val role: String = "WORKER",
)
