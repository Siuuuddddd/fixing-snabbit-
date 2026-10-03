package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "household_preference")
class HouseholdPreference(
    @Id
    @Column(name = "user_id")
    val userId: UUID,
    @Column(name = "has_pets", nullable = false)
    var hasPets: Boolean,
    @Column(name = "pet_details")
    var petDetails: String?,
    @Column(name = "food_context")
    var foodContext: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "utensil_preferences", nullable = false, columnDefinition = "jsonb")
    var utensilPreferences: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_languages", nullable = false, columnDefinition = "jsonb")
    var preferredLanguages: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var allergies: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "religious_food_restrictions", nullable = false, columnDefinition = "jsonb")
    var religiousFoodRestrictions: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessibility_needs", nullable = false, columnDefinition = "jsonb")
    var accessibilityNeeds: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "worker_constraints", nullable = false, columnDefinition = "jsonb")
    var workerConstraints: List<String>,
    @Column(name = "heavy_lifting_required", nullable = false)
    var heavyLiftingRequired: Boolean,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disallowed_chemicals", nullable = false, columnDefinition = "jsonb")
    var disallowedChemicals: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_equipment", nullable = false, columnDefinition = "jsonb")
    var requiredEquipment: List<String>,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
)
