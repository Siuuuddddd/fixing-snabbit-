package com.snabbit.prototype.mobile.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.navigation.CustomerTab
import com.snabbit.prototype.mobile.navigation.WorkerTab

@Composable
fun SnabbitBottomBar(tabs: List<CustomerTab>, selected: CustomerTab, onSelected: (CustomerTab) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

@Composable
fun SnabbitBottomBar(tabs: List<WorkerTab>, selected: WorkerTab, onSelected: (WorkerTab) -> Unit) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}
