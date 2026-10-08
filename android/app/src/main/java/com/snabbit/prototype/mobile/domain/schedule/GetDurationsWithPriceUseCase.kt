package com.snabbit.prototype.mobile.domain.schedule

import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason

class GetDurationsWithPriceUseCase(
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(serviceId: String): List<ServiceDuration> {
        val service = catalogRepository.service(serviceId)
            ?: throw DataFailure(DataFailureReason.NOT_FOUND)
        return (1..4).map { hours ->
            ServiceDuration(
                hours = hours,
                price = SchedulePrice(currentPriceMinor = service.basePricePerHourMinor * hours),
            )
        }
    }
}
