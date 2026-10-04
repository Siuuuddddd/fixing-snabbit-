package com.snabbit.prototype.mobile.ui.placeholder

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaceholderViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PlaceholderUiState())
    val state: StateFlow<PlaceholderUiState> = mutableState.asStateFlow()

    fun onEvent(event: PlaceholderUiEvent) {
        when (event) {
            PlaceholderUiEvent.PrimaryActionClicked -> mutableState.update {
                it.copy(interactionCount = it.interactionCount + 1)
            }
        }
    }
}
