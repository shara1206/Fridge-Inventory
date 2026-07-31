package com.sharawang.fridge.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharawang.fridge.R
import com.sharawang.fridge.data.backup.ImportMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissionNeeded by viewModel.permissionNeeded.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    // Storage Access Framework, so the app needs no storage permission and the user
    // decides where the file lives.
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::export) }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    LaunchedEffect(backupResult) {
        val result = backupResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (result) {
                is BackupResult.Exported ->
                    context.getString(R.string.backup_exported, result.count)
                is BackupResult.Imported ->
                    context.getString(R.string.backup_imported, result.count)
                is BackupResult.Failed ->
                    context.getString(R.string.backup_failed, result.reason ?: "")
            }
        )
        viewModel.clearBackupResult()
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(permissionNeeded) {
        if (permissionNeeded) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setting_reminders),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.setting_reminders_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { wanted ->
                        viewModel.setEnabled(wanted, hasNotificationPermission())
                    }
                )
            }

            Text(
                stringResource(R.string.section_warn_me),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2, 5).forEach { days ->
                    FilterChip(
                        selected = settings.leadDays == days,
                        onClick = { viewModel.setLeadDays(days) },
                        enabled = settings.enabled,
                        label = {
                            Text(
                                if (days == 0) {
                                    stringResource(R.string.lead_same_day)
                                } else {
                                    stringResource(R.string.lead_days, days)
                                }
                            )
                        }
                    )
                }
            }

            Text(
                stringResource(R.string.section_at),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 9, 18, 21).forEach { hour ->
                    FilterChip(
                        selected = settings.hour == hour,
                        onClick = { viewModel.setHour(hour) },
                        enabled = settings.enabled,
                        label = { Text(stringResource(R.string.hour_label, hour)) }
                    )
                }
            }

            HorizontalDivider()

            // Backup lives here rather than on the inventory screen: it is a once-a-month
            // action at most, and it does not belong next to the daily buttons.
            Text(
                stringResource(R.string.section_backup),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { exportFile.launch(viewModel.suggestedFileName()) },
                    enabled = !busy
                ) { Text(stringResource(R.string.action_export)) }
                OutlinedButton(
                    onClick = { importFile.launch(arrayOf("application/json", "text/*", "*/*")) },
                    enabled = !busy
                ) { Text(stringResource(R.string.action_import)) }
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Asked every time rather than remembered: "replace" throws away the current kitchen,
    // and that is not a preference, it is a decision about this one file.
    val pending = pendingImport
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_mode_title)) },
            text = { Text(stringResource(R.string.import_mode_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.import(pending, ImportMode.MERGE)
                }) { Text(stringResource(R.string.import_merge)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.import(pending, ImportMode.REPLACE)
                }) { Text(stringResource(R.string.import_replace)) }
            }
        )
    }
}
