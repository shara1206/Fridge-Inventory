package com.sharawang.fridge.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sharawang.fridge.AppContainer
import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.repo.InventoryRepository
import com.sharawang.fridge.data.repo.WasteReport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which entries the log shows. */
enum class HistoryFilter {
    ALL,
    EATEN,
    THROWN_OUT;

    /**
     * Entries carried over from before reasons were recorded have no reason at all, so they
     * only show under [ALL] rather than being guessed into either side.
     */
    fun accepts(event: ConsumptionEvent): Boolean = when (this) {
        ALL -> true
        EATEN -> event.reason == FinishReason.USED
        THROWN_OUT -> event.reason == FinishReason.DISCARDED
    }
}

/** One Monday-to-Sunday week of the log, newest entry first. */
data class HistoryWeek(
    val start: LocalDate,
    val events: List<ConsumptionEvent>
) {
    val end: LocalDate get() = start.plusDays(6)

    val eatenQuantity: Double
        get() = events.filter { it.reason == FinishReason.USED }.sumOf { it.quantity }

    val thrownQuantity: Double
        get() = events.filter { it.reason == FinishReason.DISCARDED }.sumOf { it.quantity }
}

data class HistoryUiState(
    /** The log grouped by week, newest week first, after [filter] is applied. */
    val weeks: List<HistoryWeek> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** False until anything at all has been logged, so the empty state can say why. */
    val hasAnyEvents: Boolean = false,
    /** Always the whole kitchen, not the filtered log: it already splits eaten from binned. */
    val report: WasteReport = WasteReport()
)

class HistoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.history(), repository.wasteReport(), filter) { events, report, f ->
            HistoryUiState(
                weeks = groupByWeek(events.filter(f::accepts)),
                filter = f,
                hasAnyEvents = events.isNotEmpty(),
                report = report
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

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

        /** The Monday on or before [date]. Weeks start on Monday, as in ISO and in China. */
        fun weekStart(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /**
         * Groups by the week each entry happened in, keyed on the date the food left rather
         * than on when it was logged. Order within a week is kept as given (newest first),
         * and weeks with nothing in them are left out.
         */
        fun groupByWeek(events: List<ConsumptionEvent>): List<HistoryWeek> =
            events.groupBy { weekStart(it.occurredOn) }
                .map { (start, inWeek) -> HistoryWeek(start, inWeek) }
                .sortedByDescending { it.start }
    }
}
