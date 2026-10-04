package com.snabbit.prototype.mobile.data.launch

import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLaunchRepositoryTest {
    @Test
    fun `stored token enables auto login before onboarding lookup`() = runTest {
        val onboarding = FakeOnboardingLocalDataSource(completed = false)
        val repository = DefaultLaunchRepository(FakeTokenStore("saved-token"), onboarding)

        assertEquals(LaunchDestination.AUTHENTICATED, repository.initialDestination())
    }

    @Test
    fun `completed onboarding routes signed out user to login`() = runTest {
        val repository = DefaultLaunchRepository(
            FakeTokenStore(null),
            FakeOnboardingLocalDataSource(completed = true),
        )

        assertEquals(LaunchDestination.LOGIN, repository.initialDestination())
    }

    @Test
    fun `completion is persisted by local data source`() = runTest {
        val onboarding = FakeOnboardingLocalDataSource(completed = false)
        val repository = DefaultLaunchRepository(FakeTokenStore(null), onboarding)

        repository.completeOnboarding()

        assertTrue(onboarding.completedState.value)
    }
}

private class FakeOnboardingLocalDataSource(completed: Boolean) : OnboardingLocalDataSource {
    val completedState = MutableStateFlow(completed)
    override val completed: Flow<Boolean> get() = completedState
    override suspend fun markCompleted() {
        completedState.value = true
    }
}

private class FakeTokenStore(private var token: String?) : TokenStore {
    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}
