package com.sharawang.fridge.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.ShelfLife
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.data.repo.ReceiptEntry
import com.sharawang.fridge.receipt.ParsedLine
import com.sharawang.fridge.receipt.ParsedReceipt
import com.sharawang.fridge.receipt.ReceiptScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewLine(
    val parsed: ParsedLine,
    val name: String,
    val keep: Boolean,
    /**
     * Guessed from the name, overridable per line. Fixing this here is much cheaper than
     * opening every item afterwards to move it to the freezer.
     */
    val storageArea: StorageArea
)

data class ScanUiState(
    val working: Boolean = false,
    val receipt: ParsedReceipt? = null,
    val lines: List<ReviewLine> = emptyList(),
    val failed: Boolean = false,
    val emptyResult: Boolean = false,
    val savedCount: Int? = null
) {
    val keepCount: Int get() = lines.count { it.keep }
}

class ScanViewModel(
    private val repository: InventoryRepository,
    private val scanner: ReceiptScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var imagePath: String? = null

    fun onImage(context: Context, uri: Uri, path: String? = null) {
        imagePath = path
        _uiState.value = ScanUiState(working = true)
        viewModelScope.launch {
            runCatching { scanner.scan(context, uri) }
                .onSuccess { receipt ->
                    _uiState.value = ScanUiState(
                        receipt = receipt,
                        // Low-confidence lines start unchecked so a bad parse can't
                        // quietly pollute the inventory.
                        lines = receipt.lines.map { line ->
                            ReviewLine(
                                parsed = line,
                                name = line.name,
                                keep = line.confidence >= 0.7f,
                                storageArea = ShelfLife.guess(line.name).area
                            )
                        },
                        // Wording lives in the UI layer so it can be localised.
                        emptyResult = receipt.lines.isEmpty()
                    )
                }
                .onFailure {
                    _uiState.value = ScanUiState(failed = true)
                }
        }
    }

    fun toggle(index: Int) = _uiState.update { state ->
        state.copy(lines = state.lines.mapIndexed { i, line ->
            if (i == index) line.copy(keep = !line.keep) else line
        })
    }

    fun rename(index: Int, name: String) = _uiState.update { state ->
        state.copy(lines = state.lines.mapIndexed { i, line ->
            // Re-guess the area from the corrected name, unless the user already picked one.
            if (i == index) line.copy(name = name) else line
        })
    }

    fun setArea(index: Int, area: StorageArea) = _uiState.update { state ->
        state.copy(lines = state.lines.mapIndexed { i, line ->
            if (i == index) line.copy(storageArea = area) else line
        })
    }

    fun keepAll(keep: Boolean) = _uiState.update { state ->
        state.copy(lines = state.lines.map { it.copy(keep = keep) })
    }

    fun commit() {
        val state = _uiState.value
        val receipt = state.receipt ?: return
        val kept = state.lines.filter { it.keep }
        if (kept.isEmpty()) return
        _uiState.update { it.copy(working = true) }
        viewModelScope.launch {
            repository.commitReceipt(
                receipt = receipt,
                entries = kept.map { line ->
                    ReceiptEntry(
                        line = line.parsed,
                        name = line.name.trim(),
                        storageArea = line.storageArea
                    )
                },
                imagePath = imagePath
            )
            _uiState.update { it.copy(working = false, savedCount = kept.size) }
        }
    }

    fun reset() { _uiState.value = ScanUiState() }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScanViewModel(container.repository, container.scanner) as T
        }
    }
}
