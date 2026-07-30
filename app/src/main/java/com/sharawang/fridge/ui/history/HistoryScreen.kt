package com.sharawang.fridge.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.repo.WasteReport
import com.sharawang.fridge.ui.theme.LocalExpiryColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                title = { Text(stringResource(R.string.title_history)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            WasteSummary(state.report)
            HorizontalDivider()

            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        HistoryRow(item = item, onRestore = { viewModel.restore(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteSummary(report: WasteReport) {
    val palette = LocalExpiryColors.current
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.waste_window),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            listOf(
                stringResource(R.string.waste_eaten, report.usedCount),
                stringResource(R.string.waste_thrown, report.discardedCount)
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            report.wastePercent
                ?.let { stringResource(R.string.waste_percent, it) }
                ?: stringResource(R.string.waste_percent_none),
            style = MaterialTheme.typography.titleMedium,
            color = if (report.wastePercent == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                palette.expired
            }
        )
        if (report.discardedCents > 0) {
            Text(
                stringResource(R.string.waste_money, money(report.discardedCents)),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (report.discardedByCategory.isNotEmpty()) {
            Text(
                stringResource(R.string.waste_by_category),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            report.discardedByCategory.take(4).forEach { (category, count) ->
                Text(
                    stringResource(category.labelRes) + "  " +
                        stringResource(R.string.waste_count_suffix, count),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (report.discardedByStore.isNotEmpty()) {
            Text(
                stringResource(R.string.waste_by_store),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            report.discardedByStore.take(4).forEach { (store, count) ->
                Text(
                    store.label + "  " + stringResource(R.string.waste_count_suffix, count),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: FoodItem, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        item.finishedOn?.toString(),
                        item.finishedReason?.let { stringResource(it.labelRes) },
                        stringResource(item.category.labelRes)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = stringResource(R.string.action_restore)
                )
            }
        }
    }
}

private fun money(cents: Int): String = "%.2f".format(cents / 100.0)
