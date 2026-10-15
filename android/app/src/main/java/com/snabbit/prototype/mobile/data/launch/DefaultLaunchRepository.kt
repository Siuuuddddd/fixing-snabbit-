package com.snabbit.prototype.mobile.data.launch

import com.snabbit.prototype.mobile.data.network.TokenRefresher
import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DefaultLaunchRepository(
    private val sessionStore: SessionStore,
    private val onboarding: OnboardingLocalDataSource,
    private val tokenRefresher: TokenRefresher,
) : LaunchRepository {
    constructor(tokenStore: TokenStore, onboarding: OnboardingLocalDataSource) : this(
        LegacyLaunchSessionStore(tokenStore),
        onboarding,
        object : TokenRefresher {
            override fun refreshAccessToken(): String? = tokenStore.accessToken()
        },
    )
    override suspend fun initialDestination(): LaunchDestination {
        val stored = sessionStore.current()
        if (stored != null) {
            val refreshed = withContext(Dispatchers.IO) { tokenRefresher.refreshAccessToken() }
            if (refreshed != null || sessionStore.current() != null) return LaunchDestination.AUTHENTICATED
        }
        return if (onboarding.completed.first()) LaunchDestination.LOGIN else LaunchDestination.ONBOARDING
    }

    override suspend fun completeOnboarding() {
        onboarding.markCompleted()
    }
}

private class LegacyLaunchSessionStore(private val tokenStore: TokenStore) : SessionStore {
    override val session: Flow<AuthSession?> = flowOf(current())
    override fun current(): AuthSession? = tokenStore.accessToken()?.let { token ->
        AuthSession(
            token,
            "Bearer",
            Long.MAX_VALUE,
            "legacy",
            SessionUser("legacy", "", "", null, false, "CUSTOMER"),
        )
    }
    override fun save(session: AuthSession) = tokenStore.saveAccessToken(session.accessToken)
    override fun clear() = tokenStore.clear()
}
