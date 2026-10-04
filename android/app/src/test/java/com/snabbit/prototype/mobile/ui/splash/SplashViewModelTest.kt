package com.snabbit.prototype.mobile.ui.splash

import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @Test
    fun `routes directly to authenticated app when a session exists`() = runTest {
        val repository = FakeLaunchRepository(LaunchDestination.AUTHENTICATED)
        val viewModel = SplashViewModel(repository, StandardTestDispatcher(testScheduler))

        advanceUntilIdle()

        assertEquals(LaunchDestination.AUTHENTICATED, viewModel.uiState.value.destination)
    }

    @Test
    fun `routes first launch to onboarding`() = runTest {
        val repository = FakeLaunchRepository(LaunchDestination.ONBOARDING)
        val viewModel = SplashViewModel(repository, StandardTestDispatcher(testScheduler))

        advanceUntilIdle()

        assertEquals(LaunchDestination.ONBOARDING, viewModel.uiState.value.destination)
    }
}

private class FakeLaunchRepository(
    private val destination: LaunchDestination,
) : LaunchRepository {
    override suspend fun initialDestination(): LaunchDestination = destination
    override suspend fun completeOnboarding() = Unit
}
