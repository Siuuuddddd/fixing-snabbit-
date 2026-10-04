package com.snabbit.prototype.mobile.data.launch

import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.flow.first

class DefaultLaunchRepository(
    private val tokenStore: TokenStore,
    private val onboarding: OnboardingLocalDataSource,
) : LaunchRepository {
    override suspend fun initialDestination(): LaunchDestination = when {
        !tokenStore.accessToken().isNullOrBlank() -> LaunchDestination.AUTHENTICATED
        onboarding.completed.first() -> LaunchDestination.LOGIN
        else -> LaunchDestination.ONBOARDING
    }

    override suspend fun completeOnboarding() {
        onboarding.markCompleted()
    }
}
