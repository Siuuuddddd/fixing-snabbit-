package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.HouseholdPreference
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface HouseholdPreferenceRepository : JpaRepository<HouseholdPreference, UUID>
