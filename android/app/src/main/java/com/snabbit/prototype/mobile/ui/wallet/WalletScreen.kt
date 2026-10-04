package com.snabbit.prototype.mobile.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.domain.wallet.LedgerEntryState
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitCard

@Composable
fun WalletScreen(state: WalletUiState, onRefresh: () -> Unit, onRetry: () -> Unit, onAmountChanged: (String) -> Unit, onAddMoney: () -> Unit, onCheckAgain: () -> Unit) {
    PullToRefreshBox(state.isRefreshing, onRefresh, Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.balance == null -> DataErrorPanel(state.error, onRetry, Modifier.align(Alignment.Center))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Snabbit Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text("Available balance", style = MaterialTheme.typography.labelLarge)
                        Text(state.balance?.availableMinor?.let(::formatInrMinor) ?: "Unavailable", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        state.balance?.let {
                            Text("Ledger ${formatInrMinor(it.ledgerBalanceMinor)} • Reserved ${formatInrMinor(it.reservedMinor)}")
                            Text("Total spent ${formatInrMinor(it.totalSpentMinor)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text("Add money", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.amountRupees,
                            onValueChange = onAmountChanged,
                            label = { Text("Amount in rupees") },
                            prefix = { Text("₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = state.phase !in setOf(AddMoneyPhase.CREATING_ORDER, AddMoneyPhase.CHECKOUT, AddMoneyPhase.VERIFYING),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Button(onAddMoney, Modifier.fillMaxWidth(), enabled = state.phase !in setOf(AddMoneyPhase.CREATING_ORDER, AddMoneyPhase.CHECKOUT, AddMoneyPhase.VERIFYING)) {
                            Text(if (state.phase == AddMoneyPhase.CREATING_ORDER) "Creating order…" else "Continue to Razorpay")
                        }
                        state.message?.let { Text(it, Modifier.padding(top = 8.dp), color = if (state.phase == AddMoneyPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.phase == AddMoneyPhase.VERIFYING) Button(onCheckAgain, Modifier.padding(top = 8.dp)) { Text("Check backend again") }
                    }
                }
                item {
                    Column {
                        Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Prototype local history — current app session only", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.history.isEmpty()) item { Text("No wallet activity in this session.") }
                items(state.history, key = { it.id }) { entry ->
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text(entry.description, fontWeight = FontWeight.SemiBold); Text(entry.state.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall) }
                            Text("+${formatInrMinor(entry.amountMinor)}", color = if (entry.state == LedgerEntryState.CONFIRMED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
