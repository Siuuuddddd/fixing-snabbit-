package com.snabbit.prototype.mobile.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.launch.LaunchDestination
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SplashUiState(
    val destination: LaunchDestination? = null,
)

class SplashViewModel(
    private val launchRepository: LaunchRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            val destination = launchRepository.initialDestination()
            delay(SPLASH_DURATION_MILLIS)
            _uiState.value = SplashUiState(destination)
        }
    }

    private companion object {
        const val SPLASH_DURATION_MILLIS = 1_200L
    }
}
