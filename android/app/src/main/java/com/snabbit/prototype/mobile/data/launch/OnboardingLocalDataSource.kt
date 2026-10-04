package com.snabbit.prototype.mobile.data.launch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface OnboardingLocalDataSource {
    val completed: Flow<Boolean>
    suspend fun markCompleted()
}

class DataStoreOnboardingLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) : OnboardingLocalDataSource {
    override val completed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    override suspend fun markCompleted() {
        dataStore.edit { preferences -> preferences[ONBOARDING_COMPLETED] = true }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
