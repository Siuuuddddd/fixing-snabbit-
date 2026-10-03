package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface FavouriteWorkerRepository : JpaRepository<FavouriteWorker, FavouriteWorkerId> {
    fun findAllByIdUserIdOrderByPriority(userId: UUID): List<FavouriteWorker>
}
