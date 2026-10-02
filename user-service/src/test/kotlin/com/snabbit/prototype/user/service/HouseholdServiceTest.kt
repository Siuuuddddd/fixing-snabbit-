package com.snabbit.prototype.user.service

import com.snabbit.prototype.user.domain.FavouriteWorker
import com.snabbit.prototype.user.domain.FavouriteWorkerId
import com.snabbit.prototype.user.domain.HouseholdPreference
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.UserStatus
import com.snabbit.prototype.user.repository.FavouriteWorkerRepository
import com.snabbit.prototype.user.repository.HouseholdPreferenceRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class HouseholdServiceTest {
    private val users = mockk<UserProfileRepository>()
    private val preferences = mockk<HouseholdPreferenceRepository>()
    private val favourites = mockk<FavouriteWorkerRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = HouseholdService(users, preferences, favourites, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `rejects a fourth favourite worker`() {
        val user = user()
        every { users.findForUpdate(user.id) } returns user
        every { favourites.findAllByIdUserIdOrderByPriority(user.id) } returns (1..3).map {
            FavouriteWorker(FavouriteWorkerId(user.id, UUID.randomUUID()), it, now)
        }

        assertThrows(FavouriteLimitReached::class.java) {
            service.addFavourite(user.id, UUID.randomUUID())
        }
        verify(exactly = 0) { favourites.save(any()) }
    }

    @Test
    fun `fills the first free favourite priority`() {
        val user = user()
        val workerId = UUID.randomUUID()
        val current = listOf(
            FavouriteWorker(FavouriteWorkerId(user.id, UUID.randomUUID()), 1, now),
            FavouriteWorker(FavouriteWorkerId(user.id, UUID.randomUUID()), 3, now),
        )
        val saved = slot<FavouriteWorker>()
        every { users.findForUpdate(user.id) } returns user
        every { favourites.findAllByIdUserIdOrderByPriority(user.id) } returns current andThen current
        every { favourites.save(capture(saved)) } answers { firstArg() }
        every { preferences.findById(user.id) } returns Optional.of(preference(user.id))

        service.addFavourite(user.id, workerId)

        assertEquals(2, saved.captured.priority)
        assertEquals(workerId, saved.captured.id.workerId)
    }

    private fun user() = UserProfile(
        id = UUID.randomUUID(),
        phoneE164 = "+919999999999",
        displayName = "Customer",
        status = UserStatus.ACTIVE,
        plusActive = true,
        plusExpiresAt = now.plusSeconds(3_600),
        createdAt = now,
        updatedAt = now,
    )

    private fun preference(userId: UUID) = HouseholdPreference(
        userId,
        false,
        null,
        null,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        false,
        emptyList(),
        emptyList(),
        now,
    )
}
