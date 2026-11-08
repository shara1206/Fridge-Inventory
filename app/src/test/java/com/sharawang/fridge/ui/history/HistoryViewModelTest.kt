package com.sharawang.fridge.ui.history

import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.ui.MainDispatcherRule
import com.sharawang.fridge.ui.TestDatabase
import com.sharawang.fridge.ui.await
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private lateinit var db: TestDatabase
    private lateinit var viewModel: HistoryViewModel

    // 2026-11-04 is a Wednesday; its week runs Monday 11-02 to Sunday 11-08.
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Before
    fun setUp() {
        db = TestDatabase()
        viewModel = HistoryViewModel(db.repository)
    }

    @After
    fun tearDown() = db.close()

    private fun event(name: String, on: LocalDate, reason: FinishReason? = FinishReason.USED) =
        ConsumptionEvent(name = name, occurredOn = on, reason = reason)

    private fun log(vararg events: ConsumptionEvent) = runBlocking {
        db.repository.insertAllEvents(events.toList())
    }

    // ---- weeks --------------------------------------------------------------

    @Test
    fun `weeks start on Monday and end on Sunday`() {
        assertEquals(LocalDate.of(2026, 11, 2), HistoryViewModel.weekStart(wednesday))
        assertEquals(
            LocalDate.of(2026, 11, 2),
            HistoryViewModel.weekStart(LocalDate.of(2026, 11, 2))
        )
        assertEquals(
            LocalDate.of(2026, 11, 2),
            HistoryViewModel.weekStart(LocalDate.of(2026, 11, 8))
        )
        assertEquals(
            LocalDate.of(2026, 11, 9),
            HistoryViewModel.weekStart(LocalDate.of(2026, 11, 9))
        )
    }

    @Test
    fun `entries group by the week the food left, newest week first`() {
        val weeks = HistoryViewModel.groupByWeek(
            listOf(
                event("Milk", LocalDate.of(2026, 11, 8)),
                event("Eggs", LocalDate.of(2026, 11, 2)),
                event("Rice", LocalDate.of(2026, 11, 1)),
                event("Tofu", LocalDate.of(2026, 10, 12))
            )
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 12)
            ),
            weeks.map { it.start }
        )
        assertEquals(listOf("Milk", "Eggs"), weeks[0].events.map { it.name })
        assertEquals(LocalDate.of(2026, 11, 8), weeks[0].end)
    }

    @Test
    fun `a week totals what was eaten and what was thrown out separately`() {
        val week = HistoryWeek(
            start = HistoryViewModel.weekStart(wednesday),
            events = listOf(
                event("Eggs", wednesday).copy(quantity = 2.0),
                event("Kale", wednesday, FinishReason.DISCARDED).copy(quantity = 0.5),
                event("Legacy", wednesday, reason = null)
            )
        )

        assertEquals(2.0, week.eatenQuantity, 0.0001)
        assertEquals(0.5, week.thrownQuantity, 0.0001)
    }

    // ---- filter -------------------------------------------------------------

    @Test
    fun `the filter shows only eaten or only thrown out`() {
        log(
            event("Eggs", wednesday),
            event("Kale", wednesday, FinishReason.DISCARDED),
            event("Legacy", wednesday, reason = null)
        )
        fun names(state: HistoryUiState) = state.weeks.flatMap { it.events }.map { it.name }

        assertEquals(3, names(viewModel.uiState.await { names(it).size == 3 }).size)

        viewModel.setFilter(HistoryFilter.EATEN)
        assertEquals(
            listOf("Eggs"),
            names(viewModel.uiState.await { it.filter == HistoryFilter.EATEN })
        )

        viewModel.setFilter(HistoryFilter.THROWN_OUT)
        assertEquals(
            listOf("Kale"),
            names(viewModel.uiState.await { it.filter == HistoryFilter.THROWN_OUT })
        )
    }

    @Test
    fun `an empty filter is told apart from an empty log`() {
        log(event("Eggs", wednesday))

        viewModel.setFilter(HistoryFilter.THROWN_OUT)

        val state = viewModel.uiState.await { it.filter == HistoryFilter.THROWN_OUT }
        assertTrue(state.weeks.isEmpty())
        assertTrue(state.hasAnyEvents)
    }

    @Test
    fun `an empty log says so`() {
        val state = viewModel.uiState.await { it.weeks.isEmpty() }
        assertFalse(state.hasAnyEvents)
    }

    // ---- no cap -------------------------------------------------------------

    @Test
    fun `the whole log is shown, not just the latest 200 entries`() {
        log(*Array(250) { i -> event("Item $i", wednesday.minusDays(i.toLong())) })

        val state = viewModel.uiState.await { s -> s.weeks.sumOf { it.events.size } == 250 }

        assertEquals("Item 249", state.weeks.last().events.last().name)
    }

    @Test
    fun `logging from the list shows up in this week`() {
        val id = runBlocking { db.repository.save(FoodItem(name = "Milk", quantity = 2.0)).id }
        runBlocking { db.repository.useAmount(db.repository.item(id)!!, 1.0) }

        val week = viewModel.uiState.await { it.weeks.isNotEmpty() }.weeks.single()

        assertEquals(HistoryViewModel.weekStart(LocalDate.now()), week.start)
        assertEquals("Milk", week.events.single().name)
    }
}
