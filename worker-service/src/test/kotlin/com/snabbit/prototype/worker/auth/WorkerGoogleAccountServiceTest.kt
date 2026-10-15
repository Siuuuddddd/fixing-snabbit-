package com.snabbit.prototype.worker.auth

import com.snabbit.prototype.platform.DevSeedWorkerIds
import com.snabbit.prototype.platform.GoogleIdentity
import com.snabbit.prototype.worker.config.WorkerDevSeedProperties
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import com.snabbit.prototype.worker.exception.InvalidWorkerSelectionGrant
import com.snabbit.prototype.worker.exception.WorkerNotRegistered
import com.snabbit.prototype.worker.repository.WorkerProfileRepository
import com.snabbit.prototype.worker.service.WorkerTierPolicy
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerGoogleAccountServiceTest {
    private val workers = mockk<WorkerProfileRepository>()
    private val grants = MemoryGrantStore()
    private val tiers = mockk<WorkerTierPolicy>()
    private val now = Instant.parse("2026-10-15T04:00:00Z")
    private val sharedEmail = "shared-workers" + "@" + "example.test"
    private val seed = WorkerDevSeedProperties(enabled = true, workerLoginEmail = sharedEmail)
    private val service = WorkerGoogleAccountService(
        workers, grants, seed, tiers, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `normal pre-provisioned worker is linked before shared picker is considered`() {
        val worker = worker(UUID.randomUUID(), seeded = false, email = sharedEmail)
        every { workers.findByGoogleSubAndSeededFalse("subject") } returns null
        every { workers.findNonSeededByEmailForUpdate(sharedEmail) } returns listOf(worker)

        val result = service.resolve(identity()) as WorkerGoogleAccountResult.Linked

        assertEquals(worker.id, result.profile.id)
        assertEquals("subject", worker.googleSub)
        assertFalse(worker.seeded)
    }

    @Test
    fun `shared development login returns exactly the stable seeded workers with public fields`() {
        val seeded = DevSeedWorkerIds.all.mapIndexed { index, id ->
            worker(id, seeded = true, email = sharedEmail, name = "Worker ${index + 1}")
        }
        every { workers.findByGoogleSubAndSeededFalse("subject") } returns null
        every { workers.findNonSeededByEmailForUpdate(sharedEmail) } returns emptyList()
        every { workers.findSeededByIds(DevSeedWorkerIds.all) } returns seeded
        every { tiers.currentTier(any()) } returns WorkerTier.STANDARD

        val result = service.resolve(identity()) as WorkerGoogleAccountResult.Picker

        assertEquals(DevSeedWorkerIds.all, result.workers.map(WorkerPickerOption::workerId))
        assertEquals(10, result.workers.size)
        assertTrue(result.selectionToken.isNotBlank())
        assertTrue(seeded.all { it.googleSub == null })
    }

    @Test
    fun `selection grant is subject-bound single-use and rejects workers outside its list`() {
        val chosen = worker(DevSeedWorkerIds.SAVITA, seeded = true, email = sharedEmail)
        every { workers.findById(chosen.id) } returns Optional.of(chosen)
        val token = grants.issue("subject", listOf(chosen.id))

        assertThrows(InvalidWorkerSelectionGrant::class.java) {
            service.select(identity(subject = "other-subject"), token, chosen.id)
        }
        assertThrows(InvalidWorkerSelectionGrant::class.java) {
            service.select(identity(), token, chosen.id)
        }

        val outsideToken = grants.issue("subject", listOf(chosen.id))
        assertThrows(InvalidWorkerSelectionGrant::class.java) {
            service.select(identity(), outsideToken, DevSeedWorkerIds.VINEETA)
        }
    }

    @Test
    fun `unregistered and inactive workers receive the same generic rejection`() {
        every { workers.findByGoogleSubAndSeededFalse("subject") } returns null
        every { workers.findNonSeededByEmailForUpdate(sharedEmail) } returns emptyList()
        val disabledSeed = DevSeedWorkerIds.all.map { worker(it, true, sharedEmail) }
        disabledSeed.first().status = WorkerStatus.SUSPENDED
        every { workers.findSeededByIds(DevSeedWorkerIds.all) } returns disabledSeed

        assertThrows(WorkerNotRegistered::class.java) { service.resolve(identity()) }
    }

    private fun identity(subject: String = "subject") = GoogleIdentity(subject, sharedEmail, "Worker", null)

    private fun worker(
        id: UUID,
        seeded: Boolean,
        email: String,
        name: String = "Worker",
    ) = WorkerProfile(
        id = id,
        phoneE164 = "seed-worker-${id.toString().takeLast(2)}",
        displayName = name,
        tier = WorkerTier.STANDARD,
        status = WorkerStatus.ACTIVE,
        currentLatitude = BigDecimal.ZERO,
        currentLongitude = BigDecimal.ZERO,
        serviceRadiusKm = BigDecimal.TEN,
        ratingSum = 0,
        ratingCount = 0,
        createdAt = now,
        updatedAt = now,
        joinedAt = now,
        email = email,
        emailVerified = true,
        seeded = seeded,
    )

    private class MemoryGrantStore : WorkerSelectionGrantStore {
        private val entries = mutableMapOf<String, Pair<String, Set<UUID>>>()

        override fun issue(googleSubject: String, eligibleWorkerIds: Collection<UUID>): String =
            UUID.randomUUID().toString().also { entries[it] = googleSubject to eligibleWorkerIds.toSet() }

        override fun consume(token: String, googleSubject: String): Set<UUID>? {
            val entry = entries.remove(token) ?: return null
            return entry.second.takeIf { entry.first == googleSubject }
        }
    }
}
