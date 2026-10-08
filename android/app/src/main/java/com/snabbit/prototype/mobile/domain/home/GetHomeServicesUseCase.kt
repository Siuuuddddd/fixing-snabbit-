package com.snabbit.prototype.mobile.domain.home

import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering

class GetHomeServicesUseCase(
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(): List<HomeServiceTile> {
        val services = catalogRepository.services()
        if (services.isEmpty()) return emptyList()
        val knownCodes = setOf(HOME_CLEANING, TOILET_CLEANING, LAUNDRY, DISH_WASHING)
        val homeCleaning = services.byCode(HOME_CLEANING)
        return listOf(
            HomeServiceTile(HomeServiceCategory.HOUSE_CLEANING, homeCleaning?.id),
            HomeServiceTile(HomeServiceCategory.DUSTING_AND_WIPING, homeCleaning?.id),
            HomeServiceTile(HomeServiceCategory.BATHROOM_CLEANING, services.byCode(TOILET_CLEANING)?.id),
            HomeServiceTile(HomeServiceCategory.WASH_FOLD_ARRANGE, services.byCode(LAUNDRY)?.id),
            HomeServiceTile(HomeServiceCategory.CLEANING_DISHES, services.byCode(DISH_WASHING)?.id),
            HomeServiceTile(HomeServiceCategory.MORE, services.firstOrNull { it.code !in knownCodes }?.id),
        )
    }

    private fun List<ServiceOffering>.byCode(code: String) = firstOrNull { it.code == code }

    private companion object {
        const val HOME_CLEANING = "HOME_CLEANING"
        const val TOILET_CLEANING = "TOILET_CLEANING"
        const val LAUNDRY = "LAUNDRY"
        const val DISH_WASHING = "DISH_WASHING"
    }
}
