package com.snabbit.prototype.mobile.ui.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.domain.plus.PlusPlan
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitCard

@Composable
fun PlusScreen(state: PlusUiState, onRefresh: () -> Unit, onRetry: () -> Unit, onJoin: (PlusPlan) -> Unit, onCheckAgain: () -> Unit) {
    PullToRefreshBox(state.isRefreshing, onRefresh, Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.status == null -> DataErrorPanel(state.error, onRetry, Modifier.align(Alignment.Center))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column {
                        Text("Snabbit Plus", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("More value on every booking", style = MaterialTheme.typography.titleMedium)
                    }
                }
                state.status?.takeIf { it.active }?.let { status ->
                    item {
                        SnabbitCard(Modifier.fillMaxWidth()) {
                            Text("Membership active", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            Text("${status.availablePasses} discounted passes available")
                            status.expiresAt?.let { Text("Valid until $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text("What you get", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("• Four discounted service passes")
                        Text("• ₹30 off each eligible pass")
                        Text("• Benefits confirmed and tracked by Snabbit")
                    }
                }
                state.plans.firstOrNull()?.let { plan ->
                    item {
                        SnabbitCard(Modifier.fillMaxWidth()) {
                            Text("Monthly plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${formatInrMinor(plan.priceMinor)} for ${plan.durationDays} days", style = MaterialTheme.typography.headlineSmall)
                            Text("Prototype plan display — final price and benefits are controlled by the backend.", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { onJoin(plan) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                enabled = state.status?.active != true && state.phase !in setOf(PlusPurchasePhase.CREATING_ORDER, PlusPurchasePhase.CHECKOUT, PlusPurchasePhase.VERIFYING),
                            ) { Text(if (state.phase == PlusPurchasePhase.CREATING_ORDER) "Creating order…" else "Join with Razorpay") }
                        }
                    }
                }
                state.message?.let { message ->
                    item {
                        SnabbitCard(Modifier.fillMaxWidth()) {
                            Text(message, color = if (state.phase == PlusPurchasePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            if (state.phase == PlusPurchasePhase.VERIFYING) Button(onCheckAgain, Modifier.padding(top = 8.dp)) { Text("Check backend again") }
                        }
                    }
                }
            }
        }
    }
}
