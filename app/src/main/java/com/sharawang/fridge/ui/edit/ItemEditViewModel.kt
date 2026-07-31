package com.sharawang.fridge.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.ShelfLife
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.data.repo.MergeOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditUiState(
    val id: Long = 0,
    val name: String = "",
    val category: FoodCategory = FoodCategory.OTHER,
    val storageArea: StorageArea = StorageArea.FRIDGE,
    val quantityText: String = "1",
    val unit: String = "ea",
    val purchasedOn: LocalDate = LocalDate.now(),
    val expiresOn: LocalDate? = null,
    val store: Store = Store.OTHER,
    val priceText: String = "",
    val notes: String = "",
    val loading: Boolean = false,
    val saved: Boolean = false,
    /** Set when the save folded into an existing row, so the UI can say so. */
    val mergedInto: StorageArea? = null
) {
    val isNew: Boolean get() = id == 0L

    /** Blank is not an error yet — the save button is simply disabled. */
    val quantityError: Boolean
        get() = quantityText.isNotBlank() && (quantityText.toDoubleOrNull()?.let { it <= 0 } ?: true)

    val priceError: Boolean
        get() = priceText.isNotBlank() && (priceText.toDoubleOrNull()?.let { it < 0 } ?: true)

    val canSave: Boolean
        get() = name.isNotBlank() &&
            quantityText.toDoubleOrNull()?.let { it > 0 } == true &&
            !priceError

    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 1.0
}

class ItemEditViewModel(
    private val repository: InventoryRepository,
    private val itemId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState(loading = itemId != 0L))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        if (itemId != 0L) {
            viewModelScope.launch {
                repository.item(itemId)?.let { item -> _uiState.value = item.toForm() }
            }
        }
    }

    fun onName(value: String) = _uiState.update { it.copy(name = value) }

    /** Fills category / area / expiry from the shelf-life table on demand. */
    fun autofillFromName() {
        val state = _uiState.value
        if (state.name.isBlank()) return
        val guess = ShelfLife.guess(state.name)
        _uiState.update {
            it.copy(
                category = guess.category,
                storageArea = guess.storageArea,
                expiresOn = it.purchasedOn.plusDays(guess.days.toLong())
            )
        }
    }

    fun onCategory(value: FoodCategory) = _uiState.update { it.copy(category = value) }
    fun onArea(value: StorageArea) = _uiState.update { it.copy(storageArea = value) }
    fun onQuantity(value: String) = _uiState.update { it.copy(quantityText = value) }
    fun onUnit(value: String) = _uiState.update { it.copy(unit = value) }
    fun onStore(value: Store) = _uiState.update { it.copy(store = value) }
    fun onPrice(value: String) = _uiState.update { it.copy(priceText = value) }
    fun onNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun onPurchasedOn(value: LocalDate) = _uiState.update { it.copy(purchasedOn = value) }
    fun onExpiresOn(value: LocalDate?) = _uiState.update { it.copy(expiresOn = value) }

    fun shiftExpiry(days: Long) = _uiState.update {
        it.copy(expiresOn = (it.expiresOn ?: it.purchasedOn).plusDays(days))
    }

    /**
     * Records that part of the item was used. For a saved item this writes straight through,
     * because "I just used half" is a fact about now, not a pending edit — and reaching zero
     * finishes the row instead of leaving a 0-quantity ghost in the list.
     */
    fun useSome(amount: Double) {
        val state = _uiState.value
        val remaining = state.quantity - amount

        if (state.isNew) {
            _uiState.update { it.copy(quantityText = formatQuantity(remaining.coerceAtLeast(0.0))) }
            return
        }

        viewModelScope.launch {
            val item = repository.item(state.id) ?: return@launch
            repository.useAmount(item, amount)
            if (remaining > 0.0001) {
                _uiState.update { it.copy(quantityText = formatQuantity(remaining)) }
            } else {
                _uiState.update { it.copy(saved = true) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val item = FoodItem(
                id = state.id,
                name = state.name.trim(),
                category = state.category,
                storageArea = state.storageArea,
                quantity = state.quantity,
                unit = state.unit.ifBlank { "ea" },
                purchasedOn = state.purchasedOn,
                expiresOn = state.expiresOn,
                store = state.store,
                priceCents = state.priceText.toDoubleOrNull()?.let { Math.round(it * 100).toInt() },
                notes = state.notes
            )
            val outcome = repository.save(item)
            _uiState.update {
                it.copy(
                    saved = true,
                    mergedInto = (outcome as? MergeOutcome.Merged)?.let { state.storageArea }
                )
            }
        }
    }

    fun delete() {
        val id = _uiState.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.item(id)?.let { repository.delete(it) }
            _uiState.update { it.copy(saved = true) }
        }
    }

    private fun FoodItem.toForm() = EditUiState(
        id = id,
        name = name,
        category = category,
        storageArea = storageArea,
        quantityText = formatQuantity(quantity),
        unit = unit,
        purchasedOn = purchasedOn,
        expiresOn = expiresOn,
        store = store,
        priceText = priceCents?.let { "%.2f".format(it / 100.0) } ?: "",
        notes = notes
    )

    companion object {
        fun factory(container: AppContainer, itemId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ItemEditViewModel(container.repository, itemId) as T
        }

        fun formatQuantity(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
