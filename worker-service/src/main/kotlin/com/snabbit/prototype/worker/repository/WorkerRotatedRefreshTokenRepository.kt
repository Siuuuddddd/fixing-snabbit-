package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerRotatedRefreshToken
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerRotatedRefreshTokenRepository : JpaRepository<WorkerRotatedRefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): WorkerRotatedRefreshToken?
}
