package com.snabbit.prototype.mobile.ui.home

import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `loads backend catalogue on start`() = runTest {
        val service = serviceOffering()
        val viewModel = HomeViewModel(
            FakeCatalogRepository(services = listOf(service)),
            StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(listOf(service), viewModel.uiState.value.services)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `empty catalogue is an explicit content state`() = runTest {
        val viewModel = HomeViewModel(
            FakeCatalogRepository(services = emptyList()),
            StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(emptyList<ServiceOffering>(), viewModel.uiState.value.services)
        assertEquals(null, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `offline failure exposes recoverable error`() = runTest {
        val viewModel = HomeViewModel(
            FakeCatalogRepository(failure = DataFailure(DataFailureReason.OFFLINE)),
            StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(UiDataErrorReason.OFFLINE, viewModel.uiState.value.error?.reason)
    }

    @Test
    fun `pull refresh replaces catalogue content`() = runTest {
        val repository = SequencedCatalogRepository(
            mutableListOf(listOf(serviceOffering("one")), listOf(serviceOffering("two"))),
        )
        val viewModel = HomeViewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals("two", viewModel.uiState.value.services.single().id)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}

private class FakeCatalogRepository(
    private val services: List<ServiceOffering> = emptyList(),
    private val failure: Throwable? = null,
) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> {
        failure?.let { throw it }
        return services
    }

    override suspend fun service(serviceId: String): ServiceOffering? = services.firstOrNull { it.id == serviceId }
}

private class SequencedCatalogRepository(
    private val responses: MutableList<List<ServiceOffering>>,
) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> = responses.removeFirst()
    override suspend fun service(serviceId: String): ServiceOffering? = services().firstOrNull { it.id == serviceId }
}

internal fun serviceOffering(id: String = "service-id") = ServiceOffering(
    id = id,
    code = "HOME_CLEANING",
    displayName = "Home Cleaning",
    basePricePerHourMinor = 29_900,
)
