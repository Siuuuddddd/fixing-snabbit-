package com.snabbit.prototype.worker.repository

import com.snabbit.prototype.worker.domain.WorkerSkill
import com.snabbit.prototype.worker.domain.WorkerSkillId
import org.springframework.data.jpa.repository.JpaRepository

interface WorkerSkillRepository : JpaRepository<WorkerSkill, WorkerSkillId> {
    fun findAllByIdServiceCodeAndEnabledTrue(serviceCode: String): List<WorkerSkill>
}
