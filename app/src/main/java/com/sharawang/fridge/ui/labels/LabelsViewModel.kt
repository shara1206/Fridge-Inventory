package com.sharawang.fridge.ui.labels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.labels.LabelRepository
import com.sharawang.fridge.data.labels.LabelSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot result of an export, shown once in a snackbar and then cleared. */
sealed interface LabelExport {
    data class Saved(val count: Int) : LabelExport
    data class Failed(val reason: String?) : LabelExport
}

class LabelsViewModel(private val labels: LabelRepository) : ViewModel() {

    val sheet: StateFlow<LabelSheet> = labels.sheet()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // The ten fixed cards exist before any data arrives, so the screen opens on the
            // real layout rather than on a spinner that resolves into it.
            LabelSheet.from(emptyList())
        )

    private val _result = MutableStateFlow<LabelExport?>(null)
    val result: StateFlow<LabelExport?> = _result.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun suggestedFileName(): String = labels.suggestedFileName()

    /** A failed write must not look like a successful one, so every failure surfaces. */
    fun export(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        _result.value = try {
            LabelExport.Saved(labels.exportTo(uri))
        } catch (e: Exception) {
            LabelExport.Failed(e.message)
        }
        _busy.value = false
    }

    fun clearResult() { _result.value = null }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LabelsViewModel(container.labelRepository) as T
        }
    }
}
