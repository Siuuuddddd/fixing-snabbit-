package com.snabbit.prototype.user.favourite

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.response.PublicWorkerProfileSnapshot
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.FavouriteLimitReached
import com.snabbit.prototype.user.exception.NotEligibleFavourite
import com.snabbit.prototype.user.exception.PlusRequired
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FavouriteWorkerServiceTest {
    private val now = Instant.parse("2026-10-13T06:30:00Z")
    private val users = mockk<UserProfileRepository>()
    private val favourites = mockk<FavouriteWorkerRepository>()
    private val bookings = mockk<FavouriteBookingClient>()
    private val workers = mockk<FavouriteWorkerClient>()
    private val service = FavouriteWorkerService(
        users, favourites, bookings, workers, FavouriteProperties(),
        Clock.fixed(now, ZoneOffset.UTC), mockk<UserOutboxWriter>(relaxed = true),
    )

    @Test
    fun `list requires an active Plus entitlement`() {
        val user = user(plusActive = false)
        every { users.findById(user.id) } returns Optional.of(user)

        assertThrows(PlusRequired::class.java) { service.list(user.id) }
    }

    @Test
    fun `fourth favourite is rejected while holding the customer lock`() {
        val user = user()
        every { users.findForUpdate(user.id) } returns user
        every { favourites.findAllByIdUserIdOrderByPriority(user.id) } returns (1..3).map {
            FavouriteWorker(FavouriteWorkerId(user.id, UUID.randomUUID()), it, now)
        }

        assertThrows(FavouriteLimitReached::class.java) { service.add(user.id, UUID.randomUUID()) }
    }

    @Test
    fun `completed visit eligibility is required unless seeded exemption is used`() {
        val user = user()
        val workerId = UUID.randomUUID()
        every { users.findForUpdate(user.id) } returns user
        every { favourites.findAllByIdUserIdOrderByPriority(user.id) } returns emptyList()
        every { bookings.completedWorkerIds(user.id) } returns emptyList()

        assertThrows(NotEligibleFavourite::class.java) { service.add(user.id, workerId) }
    }

    @Test
    fun `suggestions order own rating before rank score`() {
        val user = user()
        val highlyRanked = profile(UUID.randomUUID(), customerRating = 4, rank = "4.90")
        val personallyRated = profile(UUID.randomUUID(), customerRating = 5, rank = "4.10")
        every { users.findById(user.id) } returns Optional.of(user)
        every { favourites.findAllByIdUserIdOrderByPriority(user.id) } returns emptyList()
        every { bookings.completedWorkerIds(user.id) } returns listOf(highlyRanked.workerId, personallyRated.workerId)
        every { workers.profiles(any(), user.id) } returns listOf(highlyRanked, personallyRated)

        val result = service.suggestions(user.id)

        assertEquals(listOf(personallyRated.workerId, highlyRanked.workerId), result.map { it.workerId })
    }

    private fun user(plusActive: Boolean = true) = UserProfile(
        UUID.randomUUID(), "+919900000001", "Customer", UserStatus.ACTIVE,
        plusActive, if (plusActive) now.plusSeconds(3600) else null, now, now,
    )

    private fun profile(workerId: UUID, customerRating: Int, rank: String) = PublicWorkerProfileSnapshot(
        workerId, "Worker", "STANDARD", BigDecimal("4.50"), 10, 20, 4,
        BigDecimal(rank), customerRating,
    )
}
