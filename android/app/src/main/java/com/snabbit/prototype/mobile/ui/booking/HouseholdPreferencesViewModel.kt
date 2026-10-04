package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.household.HouseholdPreferences
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HouseholdPreferencesUiState(
    val preferences: HouseholdPreferences = HouseholdPreferences(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: UiDataError? = null,
)

class HouseholdPreferencesViewModel(
    private val repository: HouseholdRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HouseholdPreferencesUiState())
    val uiState: StateFlow<HouseholdPreferencesUiState> = _uiState.asStateFlow()

    init { load() }
    fun retry() = load()
    fun consumeSaved() = _uiState.update { it.copy(saved = false) }
    fun setPets(value: Boolean) = edit { it.copy(hasPets = value, petDetails = if (value) it.petDetails else "") }
    fun setPetDetails(value: String) = edit { it.copy(petDetails = value.take(300)) }
    fun setFoodContext(value: String) = edit { it.copy(foodContext = value) }
    fun toggleLanguage(value: String) = edit { it.copy(preferredLanguages = it.preferredLanguages.toggle(value)) }
    fun toggleUtensil(value: String) = edit { it.copy(utensilPreferences = it.utensilPreferences.toggle(value)) }
    fun toggleAllergy(value: String) = edit { it.copy(allergies = it.allergies.toggle(value)) }
    fun toggleAccessibility(value: String) = edit { it.copy(accessibilityNeeds = it.accessibilityNeeds.toggle(value)) }
    fun toggleEquipment(value: String) = edit { it.copy(requiredEquipment = it.requiredEquipment.toggle(value)) }
    fun toggleConstraint(value: String) = edit { it.copy(workerConstraints = it.workerConstraints.toggle(value)) }
    fun setHeavyLifting(value: Boolean) = edit { it.copy(heavyLiftingRequired = value) }

    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { repository.update(_uiState.value.preferences) }
                .onSuccess { saved -> _uiState.update { it.copy(preferences = saved, isSaving = false, saved = true) } }
                .onFailure { failure -> _uiState.update { it.copy(isSaving = false, error = failure.toUiDataError()) } }
        }
    }

    private fun load() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repository.preferences() }
            .onSuccess { preferences -> _uiState.update { it.copy(preferences = preferences, isLoading = false) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
    }

    private fun edit(block: (HouseholdPreferences) -> HouseholdPreferences) = _uiState.update {
        it.copy(preferences = block(it.preferences), saved = false)
    }
    private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
}
