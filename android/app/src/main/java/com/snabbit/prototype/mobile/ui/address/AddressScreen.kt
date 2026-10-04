package com.snabbit.prototype.mobile.ui.address

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.components.StatusBadge
import com.snabbit.prototype.mobile.ui.theme.Mint
import com.snabbit.prototype.mobile.ui.theme.Peach
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(
    state: AddressUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onShowAddForm: () -> Unit,
    onHideAddForm: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onAddressLineChanged: (String) -> Unit,
    onPincodeChanged: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    bookingMode: Boolean = false,
    onAddressSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { granted -> granted }) onCaptureLocation() else onLocationPermissionDenied()
    }
    val requestLocation = {
        val granted = LOCATION_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onCaptureLocation() else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.address_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.addresses.isEmpty() && !state.showAddForm -> DataErrorPanel(
                    error = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> AddressContent(
                    state = state,
                    onShowAddForm = onShowAddForm,
                    onHideAddForm = onHideAddForm,
                    onLabelChanged = onLabelChanged,
                    onAddressLineChanged = onAddressLineChanged,
                    onPincodeChanged = onPincodeChanged,
                    onCaptureLocation = requestLocation,
                    onSave = onSave,
                    onDelete = onDelete,
                    bookingMode = bookingMode,
                    onAddressSelected = onAddressSelected,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun AddressContent(
    state: AddressUiState,
    onShowAddForm: () -> Unit,
    onHideAddForm: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onAddressLineChanged: (String) -> Unit,
    onPincodeChanged: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onRetry: () -> Unit,
    bookingMode: Boolean,
    onAddressSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        item { ServiceAreaCard() }
        state.error?.let { error ->
            item { DataErrorPanel(error = error, onRetry = onRetry) }
        }
        if (state.showAddForm) {
            item {
                AddAddressForm(
                    state = state,
                    onHide = onHideAddForm,
                    onLabelChanged = onLabelChanged,
                    onAddressLineChanged = onAddressLineChanged,
                    onPincodeChanged = onPincodeChanged,
                    onCaptureLocation = onCaptureLocation,
                    onSave = onSave,
                )
            }
        } else {
            item {
                SnabbitButton(
                    text = stringResource(R.string.address_add_action),
                    onClick = onShowAddForm,
                )
            }
        }
        item {
            Text(stringResource(R.string.address_saved_title), style = MaterialTheme.typography.titleLarge)
        }
        if (state.addresses.isEmpty()) {
            item {
                EmptyStatePanel(
                    title = stringResource(R.string.address_empty_title),
                    description = stringResource(R.string.address_empty_description),
                )
            }
        } else {
            items(state.addresses, key = SavedAddress::id) { address ->
                AddressCard(
                    address = address,
                    deleting = state.deletingAddressId == address.id,
                    onDelete = { onDelete(address.id) },
                    bookingMode = bookingMode,
                    onSelect = { onAddressSelected(address.id) },
                )
            }
        }
    }
}

@Composable
private fun ServiceAreaCard() {
    SnabbitCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            Box(
                modifier = Modifier.background(Peach, MaterialTheme.shapes.medium).padding(SnabbitSpacing.medium),
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.extraSmall)) {
                Text(stringResource(R.string.address_service_area_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.address_service_area_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AddAddressForm(
    state: AddressUiState,
    onHide: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onAddressLineChanged: (String) -> Unit,
    onPincodeChanged: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onSave: () -> Unit,
) {
    SnabbitCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.address_form_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(SnabbitSpacing.medium))
        OutlinedTextField(
            value = state.label,
            onValueChange = onLabelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.address_label_label)) },
            singleLine = true,
            enabled = !state.isSaving,
        )
        Spacer(Modifier.height(SnabbitSpacing.small))
        OutlinedTextField(
            value = state.addressLine,
            onValueChange = onAddressLineChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.address_line_label)) },
            minLines = 2,
            enabled = !state.isSaving,
        )
        Spacer(Modifier.height(SnabbitSpacing.small))
        OutlinedTextField(
            value = state.pincode,
            onValueChange = onPincodeChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.address_pincode_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !state.isSaving,
        )
        Spacer(Modifier.height(SnabbitSpacing.medium))
        OutlinedButton(
            onClick = onCaptureLocation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLocating && !state.isSaving,
        ) {
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.AddLocationAlt, contentDescription = null)
                Text(
                    text = stringResource(
                        if (state.location == null) R.string.address_capture_location
                        else R.string.address_recapture_location,
                    ),
                    modifier = Modifier.padding(start = SnabbitSpacing.small),
                )
            }
        }
        state.location?.let { location ->
            Text(
                stringResource(R.string.address_location_accuracy, location.accuracyMeters.toInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.formError?.let { error ->
            Spacer(Modifier.height(SnabbitSpacing.small))
            Text(
                stringResource(error.messageResource()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(SnabbitSpacing.medium))
        SnabbitButton(
            text = stringResource(R.string.address_save_action),
            onClick = onSave,
            loading = state.isSaving,
        )
        TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun AddressCard(
    address: SavedAddress,
    deleting: Boolean,
    bookingMode: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    SnabbitCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.background(Mint, MaterialTheme.shapes.medium).padding(SnabbitSpacing.medium),
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = SnabbitSpacing.medium)) {
                Text(address.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        R.string.address_full_format,
                        address.addressLine,
                        address.city,
                        address.pincode,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (address.verified) {
                    StatusBadge(text = stringResource(R.string.address_verified), color = ConfirmedGreen)
                }
            }
            if (deleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.address_delete_description, address.label),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (bookingMode && address.verified) {
            Spacer(Modifier.height(SnabbitSpacing.small))
            SnabbitButton(text = stringResource(R.string.address_use_action), onClick = onSelect)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.LocationOff, contentDescription = null) },
            title = { Text(stringResource(R.string.address_delete_title)) },
            text = { Text(stringResource(R.string.address_delete_message, address.label)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.address_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun AddressFormError.messageResource(): Int = when (this) {
    AddressFormError.INVALID_LABEL -> R.string.address_error_label
    AddressFormError.INVALID_ADDRESS -> R.string.address_error_line
    AddressFormError.INVALID_PINCODE -> R.string.address_error_pincode
    AddressFormError.LOCATION_REQUIRED -> R.string.address_error_location_required
    AddressFormError.LOCATION_INACCURATE -> R.string.address_error_location_accuracy
    AddressFormError.LOCATION_PERMISSION -> R.string.address_error_location_permission
    AddressFormError.LOCATION_UNAVAILABLE -> R.string.address_error_location_unavailable
    AddressFormError.LOCATION_TIMEOUT -> R.string.address_error_location_timeout
}

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
