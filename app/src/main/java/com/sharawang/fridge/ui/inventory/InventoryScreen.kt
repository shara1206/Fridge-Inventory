package com.sharawang.fridge.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.local.daysLeft
import com.sharawang.fridge.ui.theme.LocalExpiryColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onAddManual: () -> Unit,
    onScanReceipt: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenItem: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastFinished by viewModel.lastFinished.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    // Formatted through the Context because LaunchedEffect is not a composable scope.
    val context = LocalContext.current

    // Undo lives here rather than in the row so it survives the row leaving the list.
    LaunchedEffect(lastFinished) {
        val finished = lastFinished ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.inventory_finished, finished.name),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoFinish() else viewModel.clearUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = stringResource(R.string.action_history)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            stringResource(R.string.title_inventory),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            summaryLine(state),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = onAddManual) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.inventory_add_manual)
                    )
                }
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(onClick = onScanReceipt) {
                    Icon(
                        Icons.Filled.DocumentScanner,
                        contentDescription = stringResource(R.string.inventory_scan)
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.inventory_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            FilterRow(
                selectedIsAll = state.area == null,
                onSelectAll = { viewModel.setArea(null) },
                options = StorageArea.entries,
                isSelected = { it == state.area },
                onSelect = { viewModel.setArea(it) },
                labelOf = { stringResource(it.labelRes) }
            )

            // Second axis: shopping-aisle category.
            if (state.availableCategories.isNotEmpty()) {
                FilterRow(
                    selectedIsAll = state.category == null,
                    onSelectAll = { viewModel.setCategory(null) },
                    options = state.availableCategories,
                    isSelected = { it == state.category },
                    onSelect = { viewModel.setCategory(it) },
                    labelOf = { stringResource(it.labelRes) }
                )
            }

            // Third axis, only worth showing once you shop at more than one place.
            if (state.availableStores.isNotEmpty()) {
                FilterRow(
                    selectedIsAll = state.store == null,
                    onSelectAll = { viewModel.setStore(null) },
                    options = state.availableStores,
                    isSelected = { it == state.store },
                    onSelect = { viewModel.setStore(it) },
                    labelOf = { it.label }
                )
            }

            if (state.items.isEmpty()) {
                Text(
                    stringResource(R.string.inventory_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            onOpen = { onOpenItem(item.id) },
                            onFinish = { reason -> viewModel.finish(item, reason) },
                            onBuyAnother = { viewModel.buyAnother(item) },
                            onUseOne = { viewModel.useOne(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun summaryLine(state: InventoryUiState): String = buildList {
    add(stringResource(R.string.inventory_summary_items, state.items.size))
    if (state.expired > 0) add(stringResource(R.string.inventory_summary_expired, state.expired))
    if (state.expiringSoon > 0) {
        add(stringResource(R.string.inventory_summary_due, state.expiringSoon))
    }
}.joinToString(" · ")

@Composable
private fun <T> FilterRow(
    selectedIsAll: Boolean,
    onSelectAll: () -> Unit,
    options: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedIsAll,
            onClick = onSelectAll,
            label = { Text(stringResource(R.string.filter_all)) }
        )
        options.forEach { option ->
            FilterChip(
                selected = isSelected(option),
                onClick = { onSelect(option) },
                label = { Text(labelOf(option)) }
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: FoodItem,
    onOpen: () -> Unit,
    onFinish: (FinishReason) -> Unit,
    onBuyAnother: () -> Unit,
    onUseOne: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        // Two lines rather than one: the stepper needs real tap targets, and squeezing it
        // in beside the expiry chip and the two finish buttons left nothing legible.
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                ExpiryChip(item)
                // Two separate outcomes on purpose: without them the waste report can only
                // report that food vanished.
                IconButton(onClick = { onFinish(FinishReason.USED) }) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.inventory_mark_used)
                    )
                }
                IconButton(onClick = { onFinish(FinishReason.DISCARDED) }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.inventory_mark_discarded),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    listOfNotNull(
                        stringResource(item.category.labelRes),
                        item.store.takeIf { it != Store.OTHER }?.label
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                QuantityStepper(
                    quantity = item.quantity,
                    onDecrease = onUseOne,
                    onIncrease = onBuyAnother
                )
            }
        }
    }
}

/**
 * −  2  +
 *
 * A bare count, no unit. "2 bunch" was never worth the field it cost: what you want to know
 * at a glance is how many of the thing are left, and the name already says what the thing is.
 *
 * Both buttons report something that happened in the kitchen rather than editing a number:
 * + is "bought another", − is "ate one". So − runs all the way to zero, where it finishes
 * the row as eaten — the same outcome as ✓, undo included.
 */
@Composable
private fun QuantityStepper(
    quantity: Double,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onDecrease,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.qty_decrease),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            trim(quantity),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 4.dp)
        )
        FilledTonalIconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.qty_increase),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Shown only for food that is [DUE_SOON_DAYS] days out or already past it.
 *
 * A chip on every row taught you to stop reading chips. "—" on a tin of soy sauce and "9d"
 * on yogurt you will obviously finish tomorrow are both noise, and noise is what makes the
 * one row that actually needs eating tonight invisible. Nothing here means nothing to do.
 */
@Composable
private fun ExpiryChip(item: FoodItem) {
    val days = item.daysLeft(LocalDate.now()) ?: return
    if (days > DUE_SOON_DAYS) return

    val palette = LocalExpiryColors.current
    val text = when {
        days < 0 -> stringResource(R.string.expiry_over, -days)
        days == 0L -> stringResource(R.string.expiry_today)
        else -> stringResource(R.string.expiry_days, days)
    }
    val color = when {
        days < 0 -> palette.expired
        days <= 2 -> palette.dueSoon
        else -> palette.fresh
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text, color = color, style = MaterialTheme.typography.labelMedium) }
    )
}

private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
