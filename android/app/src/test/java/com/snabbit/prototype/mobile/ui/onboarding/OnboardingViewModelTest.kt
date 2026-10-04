package com.snabbit.prototype.mobile.ui.onboarding

import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Test
    fun `advances three pages then persists completion`() = runTest {
        val repository = RecordingLaunchRepository()
        val viewModel = OnboardingViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.continueOnboarding()
        viewModel.continueOnboarding()
        assertEquals(2, viewModel.uiState.value.page)

        viewModel.continueOnboarding()
        runCurrent()

        assertTrue(repository.completed)
        assertTrue(viewModel.uiState.value.completed)
    }

    @Test
    fun `skip persists completion from any page`() = runTest {
        val repository = RecordingLaunchRepository()
        val viewModel = OnboardingViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.skip()
        runCurrent()

        assertTrue(repository.completed)
        assertTrue(viewModel.uiState.value.completed)
    }
}

private class RecordingLaunchRepository : LaunchRepository {
    var completed = false
    override suspend fun initialDestination(): LaunchDestination = LaunchDestination.ONBOARDING
    override suspend fun completeOnboarding() {
        completed = true
    }
}
