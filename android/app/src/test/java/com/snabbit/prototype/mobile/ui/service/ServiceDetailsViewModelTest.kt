package com.snabbit.prototype.mobile.ui.service

import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import com.snabbit.prototype.mobile.ui.home.serviceOffering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceDetailsViewModelTest {
    @Test
    fun `loads selected service and changes duration`() = runTest {
        val service = serviceOffering()
        val viewModel = ServiceDetailsViewModel(
            service.id,
            DetailCatalogRepository(service),
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        viewModel.selectDuration(4)

        assertEquals(service, viewModel.uiState.value.service)
        assertEquals(4, viewModel.uiState.value.selectedDurationHours)
    }

    @Test
    fun `missing selected service is a not found error`() = runTest {
        val viewModel = ServiceDetailsViewModel(
            "missing",
            DetailCatalogRepository(null),
            StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(UiDataErrorReason.NOT_FOUND, viewModel.uiState.value.error?.reason)
    }
}

private class DetailCatalogRepository(private val service: ServiceOffering?) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> = listOfNotNull(service)
    override suspend fun service(serviceId: String): ServiceOffering? = service?.takeIf { it.id == serviceId }
}
