package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.domain.enums.AssignmentType
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy
import com.snabbit.prototype.booking.dto.request.WorkerMatchRequest
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.matching.MatchingClients
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AssignmentRecoveryService(
    private val lifecycle: BookingLifecycleService,
    private val catalog: ServiceCatalogService,
    private val clients: MatchingClients,
) {
    suspend fun recover(bookingId: UUID, visitIndex: Int, failedWorkerId: UUID): BookingSnapshot {
        val context = lifecycle.recoveryContext(bookingId, visitIndex, failedWorkerId)
        if (context.fallbackPolicy == FallbackPolicy.SKIP_AND_REFUND) {
            return lifecycle.refundVisitForDropout(bookingId, visitIndex, failedWorkerId)
        }
        val offering = catalog.requireActive(context.serviceId)
        val requirements = context.requirements
        val candidates = clients.matches(
            WorkerMatchRequest(
                serviceCode = offering.code,
                basePricePerHourMinor = offering.basePricePerHourMinor,
                startsAt = context.startsAt,
                durationMinutes = context.durationMinutes,
                latitude = context.latitude.toDouble(),
                longitude = context.longitude.toDouble(),
                maxDistanceKm = 15.0,
                hasPets = requirements.hasPets,
                foodContext = requirements.foodContext,
                utensilPreferences = requirements.utensilPreferences,
                preferredLanguages = requirements.preferredLanguages,
                allergies = requirements.allergies,
                religiousFoodRestrictions = requirements.religiousFoodRestrictions,
                workerConstraints = requirements.workerConstraints,
                heavyLiftingRequired = requirements.heavyLiftingRequired,
                disallowedChemicals = requirements.disallowedChemicals,
                requiredEquipment = requirements.requiredEquipment,
                accessibilityNeeds = requirements.accessibilityNeeds,
                favouriteWorkerIds = listOfNotNull(context.favouriteWorkerId),
                plusActive = false,
                expertPreference = ExpertPreference.ANY.name,
                favouriteWorkerId = null,
                fallbackPolicy = context.fallbackPolicy.name,
                requiredTier = context.assignedWorkerTier,
            ),
        ).firstOrNull { it.workerId != failedWorkerId }
        return if (candidates != null) {
            lifecycle.reassignVisit(
                bookingId,
                visitIndex,
                failedWorkerId,
                candidates.workerId,
                candidates.tier,
                AssignmentType.FALLBACK_SAME_TIER,
            )
        } else {
            lifecycle.refundVisitForDropout(bookingId, visitIndex, failedWorkerId)
        }
    }
}
