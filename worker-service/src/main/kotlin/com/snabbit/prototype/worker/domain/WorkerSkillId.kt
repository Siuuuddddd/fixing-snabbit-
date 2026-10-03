package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Embeddable
data class WorkerSkillId(
    @Column(name = "worker_id")
    val workerId: UUID = UUID(0, 0),
    @Column(name = "service_code")
    val serviceCode: String = "",
) : Serializable
