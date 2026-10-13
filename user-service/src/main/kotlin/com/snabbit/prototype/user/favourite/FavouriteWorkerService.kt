package com.snabbit.prototype.user.favourite

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.config.DemoCustomerDevSeed
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.dto.response.FavouriteWorkerSnapshot
import com.snabbit.prototype.user.dto.response.FavouritesSnapshot
import com.snabbit.prototype.user.dto.response.PublicWorkerProfileSnapshot
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.FavouriteLimitReached
import com.snabbit.prototype.user.exception.NotEligibleFavourite
import com.snabbit.prototype.user.exception.PlusRequired
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.ObjectProvider

@Service
class FavouriteWorkerService(
    private val users: UserProfileRepository,
    private val favourites: FavouriteWorkerRepository,
    private val bookings: FavouriteBookingClient,
    private val workers: FavouriteWorkerClient,
    private val properties: FavouriteProperties,
    private val clock: Clock,
    private val outbox: UserOutboxWriter,
    private val devSeed: ObjectProvider<DemoCustomerDevSeed>? = null,
) {
    @Transactional(readOnly = true)
    fun list(userId: UUID): FavouritesSnapshot {
        devSeed?.ifAvailable?.ensureFor(userId)
        requirePlus(userId)
        return snapshot(userId)
    }

    @Transactional
    fun add(userId: UUID, workerId: UUID): FavouritesSnapshot {
        devSeed?.ifAvailable?.ensureFor(userId)
        return addInternal(userId, workerId, false)
    }

    @Transactional
    fun addSeededDefault(userId: UUID, workerId: UUID): FavouritesSnapshot = addInternal(userId, workerId, true)

    @Transactional
    fun remove(userId: UUID, workerId: UUID): FavouritesSnapshot {
        users.findForUpdate(userId) ?: throw UserNotFound(userId)
        favourites.deleteById(FavouriteWorkerId(userId, workerId))
        outbox.append(
            userId,
            "FavouriteWorkerRemoved",
            mapOf("userId" to userId, "workerId" to workerId),
            clock.instant(),
        )
        return snapshot(userId)
    }

    @Transactional(readOnly = true)
    fun suggestions(userId: UUID): List<PublicWorkerProfileSnapshot> {
        devSeed?.ifAvailable?.ensureFor(userId)
        requirePlus(userId)
        val existing = favourites.findAllByIdUserIdOrderByPriority(userId).map { it.id.workerId }.toSet()
        val eligible = bookings.completedWorkerIds(userId).filterNot(existing::contains)
        return workers.profiles(eligible, userId).sortedWith(
            compareByDescending<PublicWorkerProfileSnapshot> { it.customerRating ?: 0 }
                .thenByDescending(PublicWorkerProfileSnapshot::rankScore)
                .thenBy { it.workerId.toString() },
        )
    }

    private fun addInternal(userId: UUID, workerId: UUID, eligibilityExempt: Boolean): FavouritesSnapshot {
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        if (!user.hasActivePlus(clock.instant())) throw PlusRequired()
        val current = favourites.findAllByIdUserIdOrderByPriority(userId)
        if (current.any { it.id.workerId == workerId }) return snapshot(userId)
        if (current.size >= properties.maxCount) throw FavouriteLimitReached()
        if (!eligibilityExempt && properties.requireCompletedVisit && workerId !in bookings.completedWorkerIds(userId)) {
            throw NotEligibleFavourite()
        }
        if (workers.profiles(listOf(workerId), userId).none { it.workerId == workerId }) {
            throw NotEligibleFavourite()
        }
        val usedPositions = current.map(FavouriteWorker::priority).toSet()
        val position = (1..properties.maxCount).first { it !in usedPositions }
        favourites.save(
            FavouriteWorker(FavouriteWorkerId(userId, workerId), position, clock.instant(), eligibilityExempt),
        )
        outbox.append(
            userId,
            "FavouriteWorkerAdded",
            mapOf("userId" to userId, "workerId" to workerId, "position" to position),
            clock.instant(),
        )
        return snapshot(userId)
    }

    private fun requirePlus(userId: UUID) {
        val user = users.findById(userId).orElseThrow { UserNotFound(userId) }
        if (!user.hasActivePlus(clock.instant())) throw PlusRequired()
    }

    private fun snapshot(userId: UUID): FavouritesSnapshot {
        val rows = favourites.findAllByIdUserIdOrderByPriority(userId)
        val profiles = workers.profiles(rows.map { it.id.workerId }, userId).associateBy { it.workerId }
        return FavouritesSnapshot(
            userId,
            properties.maxCount,
            rows.mapNotNull { row -> profiles[row.id.workerId]?.let { FavouriteWorkerSnapshot(row.priority, it) } },
        )
    }
}
