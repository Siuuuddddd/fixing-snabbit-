package com.snabbit.prototype.mobile.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.launch.LaunchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val page: Int = 0,
    val isCompleting: Boolean = false,
    val completed: Boolean = false,
)

class OnboardingViewModel(
    private val launchRepository: LaunchRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectPage(page: Int) {
        _uiState.update { it.copy(page = page.coerceIn(0, LAST_PAGE)) }
    }

    fun continueOnboarding() {
        val state = _uiState.value
        if (state.page < LAST_PAGE) {
            _uiState.update { it.copy(page = it.page + 1) }
        } else if (!state.isCompleting) {
            complete()
        }
    }

    fun skip() {
        if (!_uiState.value.isCompleting) complete()
    }

    private fun complete() {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isCompleting = true) }
            launchRepository.completeOnboarding()
            _uiState.update { it.copy(isCompleting = false, completed = true) }
        }
    }

    private companion object {
        const val LAST_PAGE = 2
    }
}
