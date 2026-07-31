package com.sharawang.fridge.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.local.daysLeft
import com.sharawang.fridge.data.repo.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * How far ahead the list looks. One number so the header count and the row chips can never
 * disagree — "2 due soon" above a list where nothing is flagged is worse than either alone.
 */
const val DUE_SOON_DAYS = 7L

data class InventoryUiState(
    val items: List<FoodItem> = emptyList(),
    val query: String = "",
    val area: StorageArea? = null,
    val category: FoodCategory? = null,
    val store: Store? = null,
    /** Only the categories present in the kitchen, so the filter row stays short. */
    val availableCategories: List<FoodCategory> = emptyList(),
    /** Same idea for stores — the row is hidden entirely below two stores. */
    val availableStores: List<Store> = emptyList(),
    val expiringSoon: Int = 0,
    val expired: Int = 0
)

/**
 * What was just removed, kept only long enough to offer undo. Quantity is captured because
 * an item can be finished after being partially used, and putting it back with the original
 * amount would quietly invent food.
 */
data class UndoableFinish(
    val id: Long,
    val name: String,
    val quantity: Double
)

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val area = MutableStateFlow<StorageArea?>(null)
    private val category = MutableStateFlow<FoodCategory?>(null)
    private val store = MutableStateFlow<Store?>(null)

    private val _lastFinished = MutableStateFlow<UndoableFinish?>(null)
    val lastFinished: StateFlow<UndoableFinish?> = _lastFinished.asStateFlow()

    /** The inventory is small, so filtering in memory beats re-querying per keystroke. */
    val uiState: StateFlow<InventoryUiState> =
        combine(
            repository.activeItems(),
            query,
            area,
            category,
            store
        ) { items, q, selectedArea, selectedCategory, selectedStore ->
            val today = LocalDate.now()
            val filtered = items.filter { item ->
                (selectedArea == null || item.storageArea == selectedArea) &&
                    (selectedCategory == null || item.category == selectedCategory) &&
                    (selectedStore == null || item.store == selectedStore) &&
                    (q.isBlank() || item.name.contains(q, ignoreCase = true))
            }
            InventoryUiState(
                items = filtered,
                query = q,
                area = selectedArea,
                category = selectedCategory,
                store = selectedStore,
                // Derived from everything in the kitchen, not from the filtered list, so the
                // rows do not collapse to one chip as soon as you pick a filter.
                availableCategories = FoodCategory.entries
                    .filter { candidate -> items.any { it.category == candidate } },
                availableStores = Store.entries
                    .filter { candidate -> items.any { it.store == candidate } }
                    .takeIf { it.size > 1 }
                    .orEmpty(),
                expiringSoon = items.count { (it.daysLeft(today) ?: Long.MAX_VALUE) in 0..DUE_SOON_DAYS },
                expired = items.count { (it.daysLeft(today) ?: Long.MAX_VALUE) < 0 }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun setQuery(value: String) { query.value = value }

    fun setArea(value: StorageArea?) { area.value = value }

    fun setCategory(value: FoodCategory?) { category.value = value }

    fun setStore(value: Store?) { store.value = value }

    /**
     * The + and − on a row are *events*, not edits to a number: + means you bought another
     * one, − means you ate one. Fixing a count that was simply wrong is the edit screen's
     * job, and that path writes no history at all.
     */
    fun buyAnother(item: FoodItem) = viewModelScope.launch { repository.addOne(item) }

    /**
     * Eating the last one empties the row, which is the same thing the ✓ button does — so it
     * records USED, feeds the waste report, and offers the same undo.
     */
    fun useOne(item: FoodItem) = viewModelScope.launch {
        val emptied = item.quantity - 1.0 <= 0.0001
        repository.useAmount(item, 1.0)
        if (emptied) {
            _lastFinished.value = UndoableFinish(item.id, item.name, item.quantity)
        }
    }

    fun finish(item: FoodItem, reason: FinishReason) = viewModelScope.launch {
        repository.markFinished(item.id, reason)
        _lastFinished.value = UndoableFinish(item.id, item.name, item.quantity)
    }

    fun undoFinish() {
        val finished = _lastFinished.value ?: return
        _lastFinished.value = null
        viewModelScope.launch { repository.restore(finished.id, finished.quantity) }
    }

    /** Called once the snackbar is gone, so a stale undo cannot fire later. */
    fun clearUndo() { _lastFinished.value = null }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                InventoryViewModel(container.repository) as T
        }
    }
}
