package com.sharawang.fridge.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.repo.WasteReport
import com.sharawang.fridge.ui.theme.LocalExpiryColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
            FilterRow(selected = state.filter, onSelect = viewModel::setFilter)

            if (state.weeks.isEmpty()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            if (state.hasAnyEvents) {
                                R.string.history_empty_filtered
                            } else {
                                R.string.history_empty
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.history_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val thisWeek = HistoryViewModel.weekStart(LocalDate.now())
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.weeks.forEach { week ->
                        item(key = "week-${week.start}") {
                            WeekHeader(week = week, thisWeek = thisWeek)
                        }
                        items(week.events, key = { it.id }) { event ->
                            HistoryRow(event = event, onUndo = { viewModel.undo(event) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                HistoryFilter.ALL -> R.string.filter_all
                                HistoryFilter.EATEN -> R.string.reason_used
                                HistoryFilter.THROWN_OUT -> R.string.reason_discarded
                            }
                        )
                    )
                }
            )
        }
    }
}

/** "This week", "Last week", or the week's dates, with what left in it. */
@Composable
private fun WeekHeader(week: HistoryWeek, thisWeek: LocalDate) {
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val title = when (week.start) {
        thisWeek -> stringResource(R.string.history_this_week)
        thisWeek.minusWeeks(1) -> stringResource(R.string.history_last_week)
        else -> stringResource(
            R.string.history_week_range,
            week.start.format(dates),
            week.end.format(dates)
        )
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            listOfNotNull(
                week.eatenQuantity.takeIf { it > 0 }
                    ?.let { stringResource(R.string.waste_eaten, trim(it)) },
                week.thrownQuantity.takeIf { it > 0 }
                    ?.let { stringResource(R.string.waste_thrown, trim(it)) }
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                stringResource(R.string.waste_eaten, trim(report.usedQuantity)),
                stringResource(R.string.waste_thrown, trim(report.discardedQuantity))
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
            report.discardedByCategory.take(4).forEach { (category, quantity) ->
                Text(
                    stringResource(category.labelRes) + "  " +
                        stringResource(R.string.waste_count_suffix, trim(quantity)),
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
            report.discardedByStore.take(4).forEach { (store, quantity) ->
                Text(
                    store.label + "  " +
                        stringResource(R.string.waste_count_suffix, trim(quantity)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * One line of the log: what left, when, how much, and whether it was eaten or binned.
 *
 * Undo removes the entry rather than logging an opposite one — the log is meant to be read
 * as what happened, not reconciled as a ledger.
 */
@Composable
private fun HistoryRow(event: ConsumptionEvent, onUndo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.name + "  " + stringResource(
                        R.string.waste_count_suffix,
                        trim(event.quantity)
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    listOfNotNull(
                        event.occurredOn.toString(),
                        event.reason?.let { stringResource(it.labelRes) },
                        stringResource(event.category.labelRes)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(onClick = onUndo) {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = stringResource(R.string.action_restore)
                )
            }
        }
    }
}

/** Bare counts read better than "2.0", and halves still need to show as "0.5". */
private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun money(cents: Int): String = "%.2f".format(cents / 100.0)
