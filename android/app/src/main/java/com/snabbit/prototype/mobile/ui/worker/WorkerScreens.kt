package com.snabbit.prototype.mobile.ui.worker

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.worker.*
import com.snabbit.prototype.mobile.ui.common.*
import com.snabbit.prototype.mobile.ui.components.*
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.InProgressBlue
import com.snabbit.prototype.mobile.ui.theme.PendingAmber
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerJobFeedScreen(state: WorkerFeedUiState, onRefresh: () -> Unit, onRetry: () -> Unit,
    onAccept: (WorkerOffer) -> Unit, onReject: (WorkerOffer) -> Unit, onOpenJob: (WorkerJob) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.worker_jobs_title)) }) }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            when {
                state.loading -> LoadingStatePanel(stringResource(R.string.worker_jobs_loading))
                state.error != null && state.offers.isEmpty() && state.jobs.isEmpty() -> DataErrorPanel(state.error, onRetry)
                state.offers.isEmpty() && state.jobs.isEmpty() -> EmptyStatePanel(stringResource(R.string.worker_jobs_empty_title), stringResource(R.string.worker_jobs_empty_body))
                else -> LazyColumn(contentPadding = PaddingValues(SnabbitSpacing.standard), verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                    if (state.error != null) item { DataErrorPanel(state.error, onRetry) }
                    if (state.offers.isNotEmpty()) item { SectionTitle(stringResource(R.string.worker_new_offers)) }
                    items(state.offers, key = { it.offerId }) { offer ->
                        val seconds = offerSecondsRemaining(state.nowMillis, offer.offerExpiresAt)
                        OfferCard(offer, seconds, state.changingOfferId == offer.offerId, { onAccept(offer) }, { onReject(offer) })
                    }
                    if (state.jobs.isNotEmpty()) item { SectionTitle(stringResource(R.string.worker_my_jobs)) }
                    items(state.jobs, key = { "${it.bookingId}-${it.visitIndex}" }) { job -> JobCard(job) { onOpenJob(job) } }
                }
            }
        }
    }
}

@Composable private fun OfferCard(offer: WorkerOffer, seconds: Long, changing: Boolean, accept: () -> Unit, reject: () -> Unit) {
    SnabbitCard(Modifier.fillMaxWidth()) {
        Text(offer.serviceName, style = MaterialTheme.typography.titleMedium)
        Text(formatDateTime(offer.startsAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.worker_duration_minutes, offer.durationMinutes))
        Text(stringResource(R.string.worker_offer_timer, seconds / 60, seconds % 60), color = MaterialTheme.colorScheme.primary)
        RequirementsText(offer.requirements)
        Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small), modifier = Modifier.padding(top = SnabbitSpacing.medium)) {
            OutlinedButton(onClick = reject, enabled = seconds > 0 && !changing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.worker_decline)) }
            Button(onClick = accept, enabled = seconds > 0 && !changing, modifier = Modifier.weight(1f)) { Text(stringResource(if (changing) R.string.worker_updating else R.string.worker_accept)) }
        }
    }
}

@Composable private fun JobCard(job: WorkerJob, open: () -> Unit) {
    SnabbitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(job.serviceName, style = MaterialTheme.typography.titleMedium)
            StatusBadge(job.status.name.replace('_', ' '), statusColor(job.status))
        }
        Text(formatDateTime(job.startsAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(job.addressLabel ?: stringResource(R.string.worker_address_reveal_note))
        TextButton(onClick = open, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.worker_view_job)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkerScheduleScreen(state: WorkerScheduleUiState, onRetry: () -> Unit, onToggle: (String, WorkerAvailabilityStatus?) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.worker_schedule_title)) }) }) { padding ->
        when {
            state.loading -> LoadingStatePanel(stringResource(R.string.worker_schedule_loading), Modifier.padding(padding))
            state.error != null && state.days.isEmpty() -> DataErrorPanel(state.error, onRetry, Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(SnabbitSpacing.standard), verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                item { Text(stringResource(R.string.worker_schedule_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.error != null) item { DataErrorPanel(state.error, onRetry) }
                items(state.days, key = { it.serviceDate }) { day ->
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text(formatDate(day.serviceDate), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.worker_operating_hours), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = day.status == WorkerAvailabilityStatus.AVAILABLE, enabled = state.changingDate == null,
                                onCheckedChange = { onToggle(day.serviceDate, day.status) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkerJobDetailsScreen(state: WorkerJobDetailsUiState, onBack: () -> Unit, onRetry: () -> Unit,
    onArrive: () -> Unit, onStart: () -> Unit, onComplete: () -> Unit, onPublishLocation: () -> Unit, onPermissionDenied: () -> Unit) {
    val context = LocalContext.current
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) pendingLocationAction?.invoke() else onPermissionDenied()
        pendingLocationAction = null
    }
    fun withLocationPermission(action: () -> Unit) {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) action() else {
            pendingLocationAction = action
            launcher.launch(permissions)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.worker_job_details_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } }) }) { padding ->
        when {
            state.loading -> LoadingStatePanel(stringResource(R.string.worker_job_loading), Modifier.padding(padding))
            state.error != null && state.job == null -> DataErrorPanel(state.error, onRetry, Modifier.padding(padding))
            state.job == null -> EmptyStatePanel(stringResource(R.string.worker_job_unavailable_title), stringResource(R.string.worker_job_unavailable_body), Modifier.padding(padding))
            else -> {
                val job = state.job
                LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(SnabbitSpacing.standard), verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                    if (state.error != null) item { DataErrorPanel(state.error, onRetry) }
                    item { SnabbitCard(Modifier.fillMaxWidth()) {
                        Text(job.serviceName, style = MaterialTheme.typography.headlineSmall)
                        StatusBadge(job.status.name.replace('_', ' '), statusColor(job.status))
                        Text(formatDateTime(job.startsAt)); Text(stringResource(R.string.worker_duration_minutes, job.durationMinutes))
                    } }
                    item { SnabbitCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.worker_service_address), style = MaterialTheme.typography.titleMedium)
                        if (job.addressLine == null) Text(stringResource(R.string.worker_address_reveal_note)) else {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.LocationOn, null); Spacer(Modifier.width(8.dp)); Text(listOfNotNull(job.addressLabel, job.addressLine, job.addressPincode).joinToString(", ")) }
                        }
                    } }
                    item { SnabbitCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.worker_requirements), style = MaterialTheme.typography.titleMedium); RequirementsText(job.requirements) } }
                    if (state.locationError) item { Text(stringResource(R.string.worker_location_error), color = MaterialTheme.colorScheme.error) }
                    if (state.locationPublished) item { Text(stringResource(R.string.worker_location_published), color = MaterialTheme.colorScheme.primary) }
                    item {
                        when (job.status) {
                            WorkerJobStatus.ASSIGNED -> SnabbitButton(stringResource(R.string.worker_mark_arrived), { withLocationPermission(onArrive) }, loading = state.publishing)
                            WorkerJobStatus.ARRIVED -> SnabbitButton(stringResource(R.string.worker_start_job), onStart, loading = state.changing)
                            WorkerJobStatus.IN_PROGRESS -> SnabbitButton(stringResource(R.string.worker_complete_job), onComplete, loading = state.changing)
                            WorkerJobStatus.COMPLETED -> Text(stringResource(R.string.worker_job_complete), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (job.status == WorkerJobStatus.ARRIVED || job.status == WorkerJobStatus.IN_PROGRESS) item {
                        OutlinedButton(onClick = { withLocationPermission(onPublishLocation) }, enabled = !state.publishing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.worker_publish_location)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WorkerEarningsScreen(state: WorkerEarningsUiState, onRetry: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.worker_earnings_title)) }) }) { padding ->
        when {
            state.loading -> LoadingStatePanel(stringResource(R.string.worker_earnings_loading), Modifier.padding(padding))
            state.error != null -> DataErrorPanel(state.error, onRetry, Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).padding(SnabbitSpacing.standard), verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                SnabbitCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.worker_earnings_unavailable), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.worker_earnings_gap), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(stringResource(R.string.worker_earnings_history_gap), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun RequirementsText(requirements: WorkerRequirements) {
    val parts = buildList {
        if (requirements.hasPets) add(stringResource(R.string.worker_requirement_pets, requirements.petDetails.orEmpty()))
        requirements.foodContext?.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(requirements.utensilPreferences)
        if (requirements.preferredLanguages.isNotEmpty()) add(stringResource(R.string.worker_requirement_languages, requirements.preferredLanguages.joinToString()))
        if (requirements.allergies.isNotEmpty()) add(stringResource(R.string.worker_requirement_allergies, requirements.allergies.joinToString()))
        addAll(requirements.religiousFoodRestrictions)
        addAll(requirements.accessibilityNeeds)
        if (requirements.heavyLiftingRequired) add(stringResource(R.string.worker_requirement_heavy_lifting))
        if (requirements.disallowedChemicals.isNotEmpty()) add(stringResource(R.string.worker_requirement_avoid, requirements.disallowedChemicals.joinToString()))
        if (requirements.requiredEquipment.isNotEmpty()) add(stringResource(R.string.worker_requirement_equipment, requirements.requiredEquipment.joinToString()))
        addAll(requirements.workerConstraints)
    }
    Text(if (parts.isEmpty()) stringResource(R.string.worker_no_special_requirements) else parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)
private fun formatDateTime(value: String): String = runCatching { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.parse(value).atZone(ZoneId.systemDefault())) }.getOrDefault(value)
private fun formatDate(value: String): String = runCatching { DateTimeFormatter.ofPattern("EEE, d MMM").format(LocalDate.parse(value)) }.getOrDefault(value)
internal fun offerSecondsRemaining(nowMillis: Long, expiresAt: String): Long =
    max(0, Duration.between(Instant.ofEpochMilli(nowMillis), Instant.parse(expiresAt)).seconds)
private fun statusColor(status: WorkerJobStatus) = when (status) {
    WorkerJobStatus.ASSIGNED -> PendingAmber
    WorkerJobStatus.ARRIVED, WorkerJobStatus.IN_PROGRESS -> InProgressBlue
    WorkerJobStatus.COMPLETED -> ConfirmedGreen
}
