package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_compatibility")
class WorkerCompatibility(
    @Id
    @Column(name = "worker_id")
    val workerId: UUID,
    @Column(name = "accepts_pets", nullable = false)
    val acceptsPets: Boolean,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val languages: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_food_contexts", nullable = false, columnDefinition = "jsonb")
    val acceptedFoodContexts: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_utensil_preferences", nullable = false, columnDefinition = "jsonb")
    val supportedUtensilPreferences: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_worker_constraints", nullable = false, columnDefinition = "jsonb")
    val supportedWorkerConstraints: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_allergy_constraints", nullable = false, columnDefinition = "jsonb")
    val supportedAllergyConstraints: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_religious_food_restrictions", nullable = false, columnDefinition = "jsonb")
    val acceptedReligiousFoodRestrictions: List<String>,
    @Column(name = "can_heavy_lift", nullable = false)
    val canHeavyLift: Boolean,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_equipment", nullable = false, columnDefinition = "jsonb")
    val availableEquipment: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "avoided_chemicals", nullable = false, columnDefinition = "jsonb")
    val avoidedChemicals: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessibility_capabilities", nullable = false, columnDefinition = "jsonb")
    val accessibilityCapabilities: List<String>,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
    @Version
    var version: Long = 0,
)
