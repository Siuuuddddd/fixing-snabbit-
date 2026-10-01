package com.snabbit.prototype.worker.persistence

import com.snabbit.prototype.worker.domain.AvailabilityStatus
import com.snabbit.prototype.worker.domain.WorkerAvailability
import com.snabbit.prototype.worker.domain.WorkerCompatibility
import com.snabbit.prototype.worker.domain.WorkerProfile
import com.snabbit.prototype.worker.domain.WorkerSkill
import com.snabbit.prototype.worker.domain.WorkerSkillId
import com.snabbit.prototype.worker.domain.WorkerStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface WorkerProfileRepository : JpaRepository<WorkerProfile, UUID> {
    fun findAllByStatus(status: WorkerStatus): List<WorkerProfile>
}

interface WorkerSkillRepository : JpaRepository<WorkerSkill, WorkerSkillId> {
    fun findAllByIdServiceCodeAndEnabledTrue(serviceCode: String): List<WorkerSkill>
}

interface WorkerAvailabilityRepository : JpaRepository<WorkerAvailability, UUID> {
    fun findAllByServiceDateAndStatus(date: LocalDate, status: AvailabilityStatus): List<WorkerAvailability>
}

interface WorkerCompatibilityRepository : JpaRepository<WorkerCompatibility, UUID> {
    fun findAllByWorkerIdIn(workerIds: Collection<UUID>): List<WorkerCompatibility>
}
