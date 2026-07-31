package com.sharawang.fridge.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.ReminderSettings
import com.sharawang.fridge.data.SettingsRepository
import com.sharawang.fridge.data.backup.BackupRepository
import com.sharawang.fridge.data.backup.ImportMode
import com.sharawang.fridge.notify.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One-shot result of an export or import, shown once in a snackbar and then cleared.
 * Modelled as data rather than a formatted string so the strings stay in resources.
 */
sealed interface BackupResult {
    data class Exported(val count: Int) : BackupResult
    data class Imported(val count: Int) : BackupResult
    data class Failed(val reason: String?) : BackupResult
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    /** Set when the UI needs to ask for POST_NOTIFICATIONS before enabling. */
    private val _permissionNeeded = MutableStateFlow(false)
    val permissionNeeded: StateFlow<Boolean> = _permissionNeeded.asStateFlow()

    /**
     * [hasPermission] is passed in from the composable, which is the only place that can
     * see the grant state. Enabling without the permission asks for it first and leaves the
     * switch off — the switch reflects reality, not intent.
     */
    fun setEnabled(enabled: Boolean, hasPermission: Boolean) {
        if (enabled && !hasPermission) {
            _permissionNeeded.value = true
            return
        }
        viewModelScope.launch {
            settingsRepository.setEnabled(enabled)
            if (enabled) scheduler.schedule(settingsRepository.settings.first().hour)
            else scheduler.cancel()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionNeeded.value = false
        if (granted) setEnabled(enabled = true, hasPermission = true)
    }

    fun setLeadDays(days: Int) = viewModelScope.launch {
        settingsRepository.setLeadDays(days)
    }

    fun setHour(hour: Int) = viewModelScope.launch {
        settingsRepository.setHour(hour)
        // Re-time the job only if reminders are actually running.
        if (settingsRepository.settings.first().enabled) scheduler.schedule(hour)
    }

    // ---- backup -------------------------------------------------------------

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun suggestedFileName(): String = backupRepository.suggestedFileName()

    fun export(uri: Uri) = runBackup { BackupResult.Exported(backupRepository.exportTo(uri)) }

    fun import(uri: Uri, mode: ImportMode) = runBackup {
        BackupResult.Imported(backupRepository.importFrom(uri, mode))
    }

    /**
     * A failed import must not look like a successful one, so every failure surfaces —
     * the file the user picked may simply have been the wrong file.
     */
    private fun runBackup(block: suspend () -> BackupResult) = viewModelScope.launch {
        _busy.value = true
        _backupResult.value = try {
            block()
        } catch (e: Exception) {
            BackupResult.Failed(e.message)
        }
        _busy.value = false
    }

    fun clearBackupResult() { _backupResult.value = null }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    container.settingsRepository,
                    container.reminderScheduler,
                    container.backupRepository
                ) as T
        }
    }
}
