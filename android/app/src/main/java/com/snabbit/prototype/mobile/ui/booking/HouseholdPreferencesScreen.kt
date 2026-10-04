package com.snabbit.prototype.mobile.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.components.InfoChip
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdPreferencesScreen(
    state: HouseholdPreferencesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPetsChanged: (Boolean) -> Unit,
    onPetDetailsChanged: (String) -> Unit,
    onFoodChanged: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onUtensil: (String) -> Unit,
    onAllergy: (String) -> Unit,
    onAccessibility: (String) -> Unit,
    onEquipment: (String) -> Unit,
    onConstraint: (String) -> Unit,
    onHeavyLifting: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_household_preferences)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }) { padding ->
        if (state.isLoading) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
            ) {
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                item { ToggleRow(stringResource(R.string.household_pets), state.preferences.hasPets, onPetsChanged) }
                if (state.preferences.hasPets) item {
                    OutlinedTextField(state.preferences.petDetails, onPetDetailsChanged, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.household_pet_details)) })
                }
                item { ChoiceSection(stringResource(R.string.household_food), FOOD, setOf(state.preferences.foodContext).filter(String::isNotBlank).toSet(), onFoodChanged) }
                item { ChoiceSection(stringResource(R.string.household_languages), LANGUAGES, state.preferences.preferredLanguages, onLanguage) }
                item { ChoiceSection(stringResource(R.string.household_utensils), UTENSILS, state.preferences.utensilPreferences, onUtensil) }
                item { ChoiceSection(stringResource(R.string.household_allergies), ALLERGIES, state.preferences.allergies, onAllergy) }
                item { ChoiceSection(stringResource(R.string.household_accessibility), ACCESSIBILITY, state.preferences.accessibilityNeeds, onAccessibility) }
                item { ChoiceSection(stringResource(R.string.household_equipment), EQUIPMENT, state.preferences.requiredEquipment, onEquipment) }
                item { ChoiceSection(stringResource(R.string.household_worker_needs), CONSTRAINTS, state.preferences.workerConstraints, onConstraint) }
                item { ToggleRow(stringResource(R.string.household_heavy_lifting), state.preferences.heavyLiftingRequired, onHeavyLifting) }
                item { SnabbitButton(stringResource(R.string.household_save_action), onSave, loading = state.isSaving) }
            }
        }
    }
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked, onChanged)
    }
}

@Composable private fun ChoiceSection(title: String, options: List<Choice>, selected: Set<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
            items(options) { option -> InfoChip(stringResource(option.labelRes), { onSelect(option.value) }, option.value in selected) }
        }
    }
}

private data class Choice(val value: String, @param:StringRes val labelRes: Int)
private val FOOD = listOf(Choice("VEGETARIAN", R.string.choice_vegetarian), Choice("NON_VEGETARIAN", R.string.choice_non_vegetarian), Choice("JAIN", R.string.choice_jain))
private val LANGUAGES = listOf(Choice("HINDI", R.string.choice_hindi), Choice("MARATHI", R.string.choice_marathi), Choice("ENGLISH", R.string.choice_english))
private val UTENSILS = listOf(Choice("SEPARATE_UTENSILS", R.string.choice_separate_utensils))
private val ALLERGIES = listOf(Choice("DUST", R.string.choice_dust), Choice("FRAGRANCE", R.string.choice_fragrance))
private val ACCESSIBILITY = listOf(Choice("STEP_FREE_ACCESS", R.string.choice_step_free), Choice("HEARING_SUPPORT", R.string.choice_hearing_support))
private val EQUIPMENT = listOf(Choice("MOP", R.string.choice_mop), Choice("VACUUM", R.string.choice_vacuum))
private val CONSTRAINTS = listOf(Choice("MASK_REQUIRED", R.string.choice_mask_required), Choice("SHOE_COVERS", R.string.choice_shoe_covers))
