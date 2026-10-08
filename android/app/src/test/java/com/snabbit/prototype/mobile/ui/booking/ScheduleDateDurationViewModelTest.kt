package com.snabbit.prototype.mobile.ui.booking

import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HabitPackPromoRepository
import com.snabbit.prototype.mobile.domain.schedule.GetDateOptionsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ValidateMultiDaySelectionUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleDateDurationViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-10T02:30:00Z"), zone)
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `date and duration emit one typed selection and identical taps do not duplicate`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        val selections = mutableListOf<ScheduleSelection>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect(selections::add)
        }
        runCurrent()

        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.TODAY))
        val duration = viewModel.uiState.value.durations.first()
        viewModel.onEvent(ScheduleDateDurationEvent.DurationSelected(duration))
        viewModel.onEvent(ScheduleDateDurationEvent.DurationSelected(duration))
        advanceUntilIdle()

        assertEquals(listOf(ScheduleSelection(listOf(today.toString()), 1)), selections)
        collector.cancel()
    }

    @Test
    fun `multi-day is not confirmed until valid selection is applied`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.MULTI_DAY))
        viewModel.onEvent(ScheduleDateDurationEvent.MultiDayToggled(today))
        viewModel.onEvent(ScheduleDateDurationEvent.MultiDayApplied)
        assertFalse(viewModel.uiState.value.hasConfirmedDates)

        viewModel.onEvent(ScheduleDateDurationEvent.MultiDayToggled(today.plusDays(3)))
        viewModel.onEvent(ScheduleDateDurationEvent.MultiDayApplied)

        assertTrue(viewModel.uiState.value.hasConfirmedDates)
        assertEquals(setOf(today, today.plusDays(3)), viewModel.uiState.value.selectedDates)
        assertFalse(viewModel.uiState.value.isMultiDayMenuExpanded)
    }

    @Test
    fun `changing date clears duration`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.TODAY))
        viewModel.onEvent(ScheduleDateDurationEvent.DurationSelected(viewModel.uiState.value.durations.first()))

        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.TOMORROW))

        assertNull(viewModel.uiState.value.selectedDuration)
    }

    @Test
    fun `switching from a single day to multi-day starts an empty explicit selection`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.TODAY))

        viewModel.onEvent(ScheduleDateDurationEvent.DateOptionSelected(ScheduleDateOption.MULTI_DAY))

        assertTrue(viewModel.uiState.value.pendingMultiDates.isEmpty())
        assertTrue(viewModel.uiState.value.selectedDates.isEmpty())
        assertFalse(viewModel.uiState.value.hasConfirmedDates)
    }

    private fun viewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = ScheduleDateDurationViewModel(
        getDateOptions = GetDateOptionsUseCase(clock),
        getDurationsWithPrice = GetDurationsWithPriceUseCase(DateCatalogRepository()),
        validateMultiDaySelection = ValidateMultiDaySelectionUseCase(clock),
        getHabitPackPromo = GetHabitPackPromoUseCase(DatePromoRepository()),
        serviceId = "service",
        dispatcher = dispatcher,
    )
}

private class DateCatalogRepository : CatalogRepository {
    private val service = ServiceOffering("service", "HOUSE_CLEANING", "House Help", 13_500)
    override suspend fun services() = listOf(service)
    override suspend fun service(serviceId: String) = service.takeIf { it.id == serviceId }
}

private class DatePromoRepository : HabitPackPromoRepository {
    override suspend fun promo() = HabitPackPromo(3, 89_700, 27_900)
}
