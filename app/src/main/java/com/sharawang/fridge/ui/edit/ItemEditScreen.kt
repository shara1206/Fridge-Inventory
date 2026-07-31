package com.sharawang.fridge.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(viewModel: ItemEditViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPurchasedPicker by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // On a merge, say so before leaving — otherwise the item appears to have vanished.
    LaunchedEffect(state.saved) {
        if (!state.saved) return@LaunchedEffect
        val mergedArea = state.mergedInto
        if (mergedArea != null) {
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.merged_into_existing,
                    context.getString(mergedArea.labelRes)
                )
            )
        }
        onDone()
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
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.title_add_item else R.string.title_edit_item
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    // Save lives here as well as at the bottom: the form is long enough
                    // that the bottom button is often off-screen while you are editing.
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = viewModel::autofillFromName) {
                Text(stringResource(R.string.action_guess))
            }

            // Corrections, not events. The −/+ on the inventory list mean "ate one" and
            // "bought another" and write history; these two only fix a number that was
            // wrong, and nothing about them reaches the waste report.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { viewModel.stepQuantity(-1.0) },
                    enabled = state.quantity > 1.0
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.qty_decrease)
                    )
                }
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = viewModel::onQuantity,
                    label = { Text(stringResource(R.string.field_qty)) },
                    singleLine = true,
                    isError = state.quantityError,
                    supportingText = if (state.quantityError) {
                        { Text(stringResource(R.string.error_qty)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(onClick = { viewModel.stepQuantity(1.0) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.qty_increase)
                    )
                }
            }

            OutlinedTextField(
                value = state.priceText,
                onValueChange = viewModel::onPrice,
                label = { Text(stringResource(R.string.field_price)) },
                singleLine = true,
                isError = state.priceError,
                supportingText = if (state.priceError) {
                    { Text(stringResource(R.string.error_price)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel(stringResource(R.string.section_use_some))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25, 0.5, 1.0).forEach { amount ->
                    OutlinedButton(onClick = { viewModel.useSome(amount) }) {
                        Text(
                            stringResource(
                                R.string.use_amount,
                                ItemEditViewModel.formatQuantity(amount)
                            )
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_where))
            ChipRow(
                options = StorageArea.entries.map { it to stringResource(it.labelRes) },
                selected = state.storageArea,
                onSelect = viewModel::onArea
            )

            SectionLabel(stringResource(R.string.section_category))
            ChipRow(
                options = FoodCategory.entries.map { it to stringResource(it.labelRes) },
                selected = state.category,
                onSelect = viewModel::onCategory
            )

            SectionLabel(stringResource(R.string.section_store))
            ChipRow(
                options = Store.entries.map { it to it.label },
                selected = state.store,
                onSelect = viewModel::onStore
            )

            SectionLabel(stringResource(R.string.section_dates))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showPurchasedPicker = true }) {
                    Text(stringResource(R.string.date_bought, state.purchasedOn.toString()))
                }
                OutlinedButton(onClick = { showExpiryPicker = true }) {
                    Text(
                        state.expiresOn
                            ?.let { stringResource(R.string.date_expires, it.toString()) }
                            ?: stringResource(R.string.date_set_expiry)
                    )
                }
            }
            // Optional, and empty until you say otherwise. The shortcuts start at two weeks
            // because anything shorter is worth picking a real date for.
            Text(
                stringResource(R.string.expiry_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { viewModel.setExpiryInWeeks(2) }) {
                    Text(stringResource(R.string.expiry_2_weeks))
                }
                TextButton(onClick = { viewModel.setExpiryInMonths(1) }) {
                    Text(stringResource(R.string.expiry_1_month))
                }
                TextButton(onClick = { viewModel.setExpiryInMonths(3) }) {
                    Text(stringResource(R.string.expiry_3_months))
                }
                TextButton(
                    onClick = { viewModel.onExpiresOn(null) },
                    enabled = state.expiresOn != null
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = { Text(stringResource(R.string.field_notes)) },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_save)) }

            // Delete moved off the app bar, where it sat one thumb-width from Back. Down
            // here it is deliberate, and it asks first.
            if (!state.isNew) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showPurchasedPicker) {
        DatePickerSheet(
            initial = state.purchasedOn,
            onDismiss = { showPurchasedPicker = false },
            onPick = { viewModel.onPurchasedOn(it); showPurchasedPicker = false }
        )
    }
    if (showExpiryPicker) {
        DatePickerSheet(
            initial = state.expiresOn ?: LocalDate.now(),
            onDismiss = { showExpiryPicker = false },
            onPick = { viewModel.onExpiresOn(it); showExpiryPicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    FilterChip(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        // M3's DatePicker works in UTC millis; converting through the local zone would
        // shift the date by a day for anyone west of UTC.
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
