package com.snabbit.prototype.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
import com.snabbit.prototype.mobile.domain.auth.PendingWorkerSelectionStore
import com.snabbit.prototype.mobile.domain.auth.SelectWorkerProfileUseCase
import com.snabbit.prototype.mobile.domain.auth.WorkerPickerOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkerPickerUiState(
    val workers: List<WorkerPickerOption> = emptyList(),
    val isLoading: Boolean = false,
    val selectedWorkerId: String? = null,
    val error: WorkerPickerError? = null,
    val authenticated: Boolean = false,
)

enum class WorkerPickerError { EXPIRED, OFFLINE, RATE_LIMITED, CANCELLED, UNKNOWN }

class WorkerPickerViewModel(
    private val provider: GoogleIdTokenProvider,
    private val selectWorker: SelectWorkerProfileUseCase,
    private val pending: PendingWorkerSelectionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WorkerPickerUiState(
            workers = pending.current()?.workers.orEmpty(),
            error = if (pending.current() == null) WorkerPickerError.EXPIRED else null,
        ),
    )
    val uiState: StateFlow<WorkerPickerUiState> = _uiState.asStateFlow()

    fun select(workerId: String) {
        if (_uiState.value.isLoading) return
        val selection = pending.current() ?: run {
            _uiState.update { it.copy(error = WorkerPickerError.EXPIRED) }
            return
        }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, selectedWorkerId = workerId, error = null) }
            runCatching { selectWorker(selection.selectionToken, workerId) }
                .onSuccess {
                    pending.clear()
                    _uiState.update { state -> state.copy(isLoading = false, authenticated = true) }
                }
                .onFailure { failure ->
                    val error = when ((failure as? AuthFailure)?.reason) {
                        AuthFailureReason.INVALID_SELECTION_TOKEN -> WorkerPickerError.EXPIRED
                        AuthFailureReason.OFFLINE -> WorkerPickerError.OFFLINE
                        AuthFailureReason.RATE_LIMITED -> WorkerPickerError.RATE_LIMITED
                        AuthFailureReason.CANCELLED -> WorkerPickerError.CANCELLED
                        else -> WorkerPickerError.UNKNOWN
                    }
                    _uiState.update { it.copy(isLoading = false, selectedWorkerId = null, error = error) }
                }
        }
    }
}
