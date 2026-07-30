package com.sharawang.fridge.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.receipt.ReceiptImageStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var captureTarget by remember { mutableStateOf<Pair<String, Uri>?>(null) }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val target = captureTarget
        if (success && target != null) {
            viewModel.onImage(context, target.second, target.first)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onImage(context, uri) }

    LaunchedEffect(state.savedCount) { if (state.savedCount != null) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                title = { Text(stringResource(R.string.title_scan)) },
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
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val (file, uri) = ReceiptImageStore.newCaptureTarget(context)
                    captureTarget = file.absolutePath to uri
                    takePhoto.launch(uri)
                }) { Text(stringResource(R.string.scan_take_photo)) }
                OutlinedButton(onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text(stringResource(R.string.scan_pick_image)) }
            }

            when {
                state.working -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.scan_reading))
                }

                state.receipt != null -> {
                    val receipt = state.receipt!!
                    Text(
                        stringResource(
                            R.string.scan_header,
                            receipt.store.label,
                            receipt.purchasedOn?.toString()
                                ?: stringResource(R.string.scan_date_unknown),
                            state.keepCount,
                            state.lines.size
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (state.emptyResult) {
                        Text(
                            stringResource(R.string.scan_no_lines),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.keepAll(true) }) {
                            Text(stringResource(R.string.scan_select_all))
                        }
                        TextButton(onClick = { viewModel.keepAll(false) }) {
                            Text(stringResource(R.string.scan_select_none))
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.lines) { index, line ->
                            ReviewRow(
                                line = line,
                                onToggle = { viewModel.toggle(index) },
                                onRename = { viewModel.rename(index, it) },
                                onArea = { viewModel.setArea(index, it) }
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::commit,
                        enabled = state.keepCount > 0,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) { Text(stringResource(R.string.scan_add_items, state.keepCount)) }
                }

                else -> Text(
                    stringResource(
                        if (state.failed) R.string.scan_unreadable else R.string.scan_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(
    line: ReviewLine,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onArea: (StorageArea) -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked = line.keep, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = line.name,
                onValueChange = onRename,
                singleLine = true,
                label = {
                    Text(
                        line.parsed.priceCents
                            ?.let { "%.2f".format(it / 100.0) }
                            ?: stringResource(R.string.scan_no_price)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StorageArea.entries.forEach { area ->
                    FilterChip(
                        selected = line.storageArea == area,
                        onClick = { onArea(area) },
                        label = {
                            Text(
                                stringResource(area.labelRes),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
            Text(
                line.parsed.rawLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
