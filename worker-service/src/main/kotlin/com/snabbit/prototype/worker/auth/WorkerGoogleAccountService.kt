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
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkerGoogleAccountService(
    private val workers: WorkerProfileRepository,
    private val grants: WorkerSelectionGrantStore,
    private val seed: WorkerDevSeedProperties,
    private val tiers: WorkerTierPolicy,
    private val clock: Clock,
) {
    @Transactional
    fun resolve(identity: GoogleIdentity): WorkerGoogleAccountResult {
        workers.findByGoogleSubAndSeededFalse(identity.subject)?.let { worker ->
            ensureActive(worker)
            worker.linkGoogleIdentity(identity.subject, identity.email, identity.displayName, identity.pictureUrl, clock.instant())
            return WorkerGoogleAccountResult.Linked(worker)
        }

        val emailMatches = workers.findNonSeededByEmailForUpdate(identity.email)
        if (emailMatches.size > 1) throw WorkerNotRegistered()
        emailMatches.singleOrNull()?.let { worker ->
            ensureActive(worker)
            if (worker.googleSub != null && worker.googleSub != identity.subject) throw WorkerNotRegistered()
            worker.linkGoogleIdentity(identity.subject, identity.email, identity.displayName, identity.pictureUrl, clock.instant())
            return WorkerGoogleAccountResult.Linked(worker)
        }

        if (!seed.enabled || !identity.email.equals(seed.workerLoginEmail, ignoreCase = true)) {
            throw WorkerNotRegistered()
        }
        val byId = workers.findSeededByIds(DevSeedWorkerIds.all).associateBy(WorkerProfile::id)
        val selectable = DevSeedWorkerIds.all.map { id ->
            val worker = byId[id]?.takeIf {
                it.status == WorkerStatus.ACTIVE && it.seeded && it.email.equals(seed.workerLoginEmail, ignoreCase = true)
            }
                ?: throw WorkerNotRegistered()
            WorkerPickerOption(worker.id, worker.displayName.trim().substringBefore(' '), tiers.currentTier(worker))
        }
        val token = grants.issue(identity.subject, selectable.map(WorkerPickerOption::workerId))
        return WorkerGoogleAccountResult.Picker(token, selectable)
    }

    @Transactional(readOnly = true)
    fun select(identity: GoogleIdentity, selectionToken: String, workerId: UUID): WorkerProfile {
        val eligible = grants.consume(selectionToken, identity.subject) ?: throw InvalidWorkerSelectionGrant()
        if (workerId !in eligible || workerId !in DevSeedWorkerIds.all) throw InvalidWorkerSelectionGrant()
        val worker = workers.findById(workerId).orElseThrow(::InvalidWorkerSelectionGrant)
        if (!worker.seeded || worker.status != WorkerStatus.ACTIVE ||
            !worker.email.equals(seed.workerLoginEmail, ignoreCase = true) ||
            !identity.email.equals(seed.workerLoginEmail, ignoreCase = true)
        ) throw InvalidWorkerSelectionGrant()
        return worker
    }

    private fun ensureActive(worker: WorkerProfile) {
        if (worker.status != WorkerStatus.ACTIVE) throw WorkerNotRegistered()
    }
}

sealed interface WorkerGoogleAccountResult {
    data class Linked(val profile: WorkerProfile) : WorkerGoogleAccountResult
    data class Picker(val selectionToken: String, val workers: List<WorkerPickerOption>) : WorkerGoogleAccountResult
}

data class WorkerPickerOption(
    val workerId: UUID,
    val name: String,
    val tier: WorkerTier,
)
