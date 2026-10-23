package com.sharawang.fridge.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.data.repo.WasteReport
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    /** The consumption log, newest first — one line per − tap and per ✓. */
    val events: List<ConsumptionEvent> = emptyList(),
    val report: WasteReport = WasteReport()
)

class HistoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.history(), repository.wasteReport()) { events, report ->
            HistoryUiState(events = events, report = report)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /**
     * Takes one entry back out of the log and puts its quantity back on the item — whether
     * that means un-finishing the row or just adding to a count that is still there.
     */
    fun undo(event: ConsumptionEvent) = viewModelScope.launch {
        repository.undoConsumption(event.id)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(container.repository) as T
        }
    }
}
