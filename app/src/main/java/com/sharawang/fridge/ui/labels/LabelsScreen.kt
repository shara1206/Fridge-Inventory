package com.sharawang.fridge.ui.labels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.labels.LabelPalette
import com.sharawang.fridge.data.labels.LabelSheet
import com.sharawang.fridge.data.labels.LabelZone
import com.sharawang.fridge.data.labels.headingRes
import com.sharawang.fridge.data.labels.labelLine

/**
 * The printable sheet, on screen.
 *
 * Deliberately not themed: these cards are a preview of paper, so they keep the colours they
 * will be printed in rather than following the app's light and dark schemes. What you see
 * scrolling here is what comes out of the printer, one card per row instead of two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(viewModel: LabelsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::export) }

    LaunchedEffect(result) {
        val outcome = result ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (outcome) {
                is LabelExport.Saved -> context.getString(R.string.labels_exported, outcome.count)
                is LabelExport.Failed ->
                    context.getString(R.string.labels_failed, outcome.reason ?: "")
            }
        )
        viewModel.clearResult()
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
                    Column {
                        Text(
                            stringResource(R.string.title_labels),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(
                                R.string.labels_count,
                                sheet.itemCount,
                                sheet.pageCount
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
                    IconButton(
                        onClick = { exportFile.launch(viewModel.suggestedFileName()) },
                        enabled = !busy
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.action_export_pdf)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.labels_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(sheet.zones) { index, zone ->
                if (index == LabelSheet.PRINTED_ZONES.size) {
                    Text(
                        stringResource(R.string.labels_extra_section),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                ZoneCard(zone = zone, index = index)
            }
        }
    }
}

@Composable
private fun ZoneCard(zone: LabelZone, index: Int) {
    val cardColor = Color(LabelPalette.cardFill(index, zone.printed))
    val chipColor = Color(LabelPalette.chipFill(index, zone.printed))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chipColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(zone.storageArea.labelRes),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
            Text(
                stringResource(zone.headingRes(index)),
                modifier = Modifier.padding(start = 8.dp),
                color = Color(LabelPalette.HEADING),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (zone.items.isEmpty()) {
                Text(
                    stringResource(R.string.labels_empty_card),
                    color = Color(LabelPalette.MUTED),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                zone.items.forEach { item ->
                    Text(
                        item.labelLine(),
                        color = Color(LabelPalette.INK),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
