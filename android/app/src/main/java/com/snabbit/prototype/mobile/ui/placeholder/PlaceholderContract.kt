package com.snabbit.prototype.mobile.ui.placeholder

data class PlaceholderUiState(val interactionCount: Int = 0)

sealed interface PlaceholderUiEvent {
    data object PrimaryActionClicked : PlaceholderUiEvent
}
