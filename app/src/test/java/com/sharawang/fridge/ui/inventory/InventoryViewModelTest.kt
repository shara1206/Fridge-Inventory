package com.sharawang.fridge.ui.inventory

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.ui.MainDispatcherRule
import com.sharawang.fridge.ui.TestDatabase
import com.sharawang.fridge.ui.await
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class InventoryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private lateinit var db: TestDatabase
    private lateinit var viewModel: InventoryViewModel

    private val repository get() = db.repository
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        db = TestDatabase()
        viewModel = InventoryViewModel(db.repository)
    }

    @After
    fun tearDown() = db.close()

    private fun add(item: FoodItem): FoodItem = runBlocking {
        repository.item(repository.save(item).id)!!
    }

    private fun history() = runBlocking { repository.history().first() }

    // ---- − is an event ------------------------------------------------------

    @Test
    fun `eating one of several lowers the count, logs it and offers undo`() {
        val eggs = add(FoodItem(name = "Eggs", quantity = 3.0))

        viewModel.useOne(eggs)

        val undo = viewModel.lastConsumption.await { it != null }!!
        assertEquals("Eggs", undo.name)
        assertFalse(undo.finished)
        assertEquals(2.0, runBlocking { repository.item(eggs.id)!!.quantity }, 0.0001)
        assertEquals(1, history().size)
    }

    @Test
    fun `eating the last one says all gone and takes the row off the list`() {
        val milk = add(FoodItem(name = "Milk", quantity = 1.0))

        viewModel.useOne(milk)

        assertTrue(viewModel.lastConsumption.await { it != null }!!.finished)
        assertEquals(today, runBlocking { repository.item(milk.id)!!.finishedOn })
        assertEquals(FinishReason.USED, history().single().reason)
    }

    @Test
    fun `undo deletes the entry and brings the row back`() {
        val milk = add(FoodItem(name = "Milk", quantity = 1.0))
        viewModel.useOne(milk)
        viewModel.lastConsumption.await { it != null }

        viewModel.undoConsumption()

        assertEquals(null, viewModel.lastConsumption.value)
        viewModel.uiState.await { state -> state.items.any { it.id == milk.id } }
        repository.history().await { it.isEmpty() }
    }

    @Test
    fun `once the snackbar is gone, undo does nothing`() {
        val milk = add(FoodItem(name = "Milk", quantity = 2.0))
        viewModel.useOne(milk)
        viewModel.lastConsumption.await { it != null }

        viewModel.clearUndo()
        viewModel.undoConsumption()

        assertEquals(1, history().size)
    }

    @Test
    fun `throwing something out logs it as waste`() {
        val kale = add(FoodItem(name = "Kale", quantity = 2.0))

        viewModel.finish(kale, FinishReason.DISCARDED)

        assertTrue(viewModel.lastConsumption.await { it != null }!!.finished)
        val event = history().single()
        assertEquals(FinishReason.DISCARDED, event.reason)
        assertEquals(2.0, event.quantity, 0.0001)
    }

    // ---- + is a purchase ----------------------------------------------------

    @Test
    fun `buying another moves the purchase date and logs nothing`() {
        val rice = add(FoodItem(name = "Rice", quantity = 1.0, purchasedOn = today.minusDays(10)))

        viewModel.buyAnother(rice)

        repository.activeItems().await { items ->
            items.single { it.id == rice.id }.let { it.quantity == 2.0 && it.purchasedOn == today }
        }
        assertTrue(history().isEmpty())
    }

    // ---- filters and counts -------------------------------------------------

    @Test
    fun `filtering narrows the list but not the filter chips`() {
        add(
            FoodItem(
                name = "Dumplings",
                category = FoodCategory.FROZEN_STAPLES,
                storageArea = StorageArea.FREEZER
            )
        )
        add(FoodItem(name = "Spinach", category = FoodCategory.VEGETABLES))

        viewModel.setArea(StorageArea.FREEZER)

        val state = viewModel.uiState.await { it.area == StorageArea.FREEZER && it.items.size == 1 }
        assertEquals("Dumplings", state.items.single().name)
        assertEquals(
            listOf(FoodCategory.VEGETABLES, FoodCategory.FROZEN_STAPLES),
            state.availableCategories
        )
    }

    @Test
    fun `search ignores case`() {
        add(FoodItem(name = "Baby Spinach"))
        add(FoodItem(name = "Tofu"))

        viewModel.setQuery("spin")

        val state = viewModel.uiState.await { it.query == "spin" && it.items.size == 1 }
        assertEquals("Baby Spinach", state.items.single().name)
    }

    @Test
    fun `the store row hides until there are two stores to choose between`() {
        add(FoodItem(name = "Tofu", store = Store.HMART))
        assertTrue(viewModel.uiState.await { it.items.size == 1 }.availableStores.isEmpty())

        add(FoodItem(name = "Bananas", store = Store.TRADER_JOES))

        assertEquals(
            listOf(Store.HMART, Store.TRADER_JOES),
            viewModel.uiState.await { it.items.size == 2 }.availableStores
        )
    }

    @Test
    fun `only dated food within a week, or past it, is counted`() {
        add(FoodItem(name = "Due soon", expiresOn = today.plusDays(DUE_SOON_DAYS)))
        add(FoodItem(name = "Past it", expiresOn = today.minusDays(1)))
        add(FoodItem(name = "Plenty of time", expiresOn = today.plusDays(DUE_SOON_DAYS + 1)))
        add(FoodItem(name = "Undated"))

        val state = viewModel.uiState.await { it.items.size == 4 }

        assertEquals(1, state.expiringSoon)
        assertEquals(1, state.expired)
    }

    // ---- backup reminder ----------------------------------------------------

    @Test
    fun `an empty kitchen is not nagged about backups`() {
        assertEquals(null, InventoryViewModel.backupReminder(false, null, today))
    }

    @Test
    fun `a kitchen that was never backed up is nagged`() {
        assertEquals(BackupReminder(null), InventoryViewModel.backupReminder(true, null, today))
    }

    @Test
    fun `a backup in the last two weeks keeps the banner away, an older one brings it back`() {
        val recent = today.minusDays(BACKUP_REMIND_DAYS - 1)
        val stale = today.minusDays(BACKUP_REMIND_DAYS)
        assertEquals(null, InventoryViewModel.backupReminder(true, recent, today))
        assertEquals(BackupReminder(stale), InventoryViewModel.backupReminder(true, stale, today))
    }

    @Test
    fun `the banner follows the stored backup date`() {
        add(FoodItem(name = "Milk"))
        val month = today.minusDays(30)
        val staleBackup = InventoryViewModel(repository, flowOf(month))
        val neverBackedUp = InventoryViewModel(repository, flowOf(null))

        assertEquals(BackupReminder(month), staleBackup.backupReminder.await { it != null })
        assertEquals(BackupReminder(null), neverBackedUp.backupReminder.await { it != null })
    }
}
