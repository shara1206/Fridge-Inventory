package com.sharawang.fridge.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.data.repo.WasteReport
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<FoodItem> = emptyList(),
    val report: WasteReport = WasteReport()
)

class HistoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.history(), repository.wasteReport()) { items, report ->
            HistoryUiState(items = items, report = report)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /** Puts an item back with the quantity it had when it was removed. */
    fun restore(item: FoodItem) = viewModelScope.launch {
        repository.restore(item.id, item.quantity)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(container.repository) as T
        }
    }
}
