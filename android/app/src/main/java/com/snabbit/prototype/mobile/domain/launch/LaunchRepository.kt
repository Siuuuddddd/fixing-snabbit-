package com.snabbit.prototype.mobile.domain.launch

enum class LaunchDestination {
    ONBOARDING,
    LOGIN,
    AUTHENTICATED,
}

interface LaunchRepository {
    suspend fun initialDestination(): LaunchDestination
    suspend fun completeOnboarding()
}
