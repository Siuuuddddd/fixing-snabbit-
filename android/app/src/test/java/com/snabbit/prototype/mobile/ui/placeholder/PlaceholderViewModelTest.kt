package com.snabbit.prototype.mobile.ui.placeholder

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholderViewModelTest {
    @Test
    fun primaryActionUpdatesImmutableState() {
        val viewModel = PlaceholderViewModel()

        viewModel.onEvent(PlaceholderUiEvent.PrimaryActionClicked)

        assertEquals(1, viewModel.state.value.interactionCount)
    }
}
