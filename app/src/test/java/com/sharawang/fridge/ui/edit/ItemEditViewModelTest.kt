package com.sharawang.fridge.ui.edit

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.ui.MainDispatcherRule
import com.sharawang.fridge.ui.TestDatabase
import com.sharawang.fridge.ui.await
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemEditViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private lateinit var db: TestDatabase

    private val repository get() = db.repository

    @Before
    fun setUp() {
        db = TestDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun add(item: FoodItem): Long = runBlocking { repository.save(item).id }

    /** Opens the editor on a saved item and waits for the form to fill. */
    private fun open(id: Long): ItemEditViewModel =
        ItemEditViewModel(repository, id).also { vm -> vm.uiState.await { !it.loading } }

    private fun history() = runBlocking { repository.history().first() }

    // ---- −/+ here are corrections ------------------------------------------

    @Test
    fun `stepping the count never goes below one`() {
        val vm = ItemEditViewModel(repository, 0)

        vm.stepQuantity(-1.0)
        vm.stepQuantity(-1.0)

        assertEquals("1", vm.uiState.value.quantityText)
    }

    @Test
    fun `correcting a count and saving writes no history`() {
        val id = add(FoodItem(name = "Rice", quantity = 3.0))
        val vm = open(id)

        vm.stepQuantity(-1.0)
        vm.save()

        vm.uiState.await { it.saved }
        assertEquals(2.0, runBlocking { repository.item(id)!!.quantity }, 0.0001)
        assertTrue(history().isEmpty())
    }

    // ---- using some is an event --------------------------------------------

    @Test
    fun `using part of a saved item is logged straight away`() {
        val id = add(FoodItem(name = "Pork Belly", quantity = 1.0))
        val vm = open(id)

        vm.useSome(0.5)

        vm.uiState.await { it.quantityText == "0.5" }
        assertFalse(vm.uiState.value.saved)
        assertEquals(0.5, history().single().quantity, 0.0001)
    }

    @Test
    fun `using the rest of a saved item finishes it as eaten and closes the editor`() {
        val id = add(FoodItem(name = "Pork Belly", quantity = 0.5))
        val vm = open(id)

        vm.useSome(0.5)

        vm.uiState.await { it.saved }
        assertEquals(FinishReason.USED, runBlocking { repository.item(id)!!.finishedReason })
    }

    @Test
    fun `using some of an unsaved item only changes the form`() {
        val vm = ItemEditViewModel(repository, 0)
        vm.onQuantity("2")

        vm.useSome(0.5)

        assertEquals("1.5", vm.uiState.value.quantityText)
        assertTrue(history().isEmpty())
    }

    // ---- saving -------------------------------------------------------------

    @Test
    fun `a new item with the same name and area folds into the existing row`() {
        add(FoodItem(name = "Tofu", storageArea = StorageArea.FRIDGE, quantity = 1.0))
        val vm = ItemEditViewModel(repository, 0)
        vm.onName("Tofu")
        vm.onArea(StorageArea.FRIDGE)

        vm.save()

        assertEquals(StorageArea.FRIDGE, vm.uiState.await { it.saved }.mergedInto)
        assertEquals(2.0, runBlocking { repository.allItems().single().quantity }, 0.0001)
    }

    @Test
    fun `a unit stored by an older version survives an edit`() {
        val id = add(FoodItem(name = "Scallions", unit = "bunch"))
        val vm = open(id)

        vm.onNotes("for the pancakes")
        vm.save()

        vm.uiState.await { it.saved }
        assertEquals("bunch", runBlocking { repository.item(id)!!.unit })
    }

    @Test
    fun `price is stored as whole cents`() {
        val vm = ItemEditViewModel(repository, 0)
        vm.onName("Kimchi")
        vm.onPrice("4.99")

        vm.save()

        vm.uiState.await { it.saved }
        assertEquals(499, runBlocking { repository.allItems().single().priceCents })
    }

    @Test
    fun `expiry starts empty and shortcuts count from the purchase date`() {
        val vm = ItemEditViewModel(repository, 0)
        assertNull(vm.uiState.value.expiresOn)

        val bought = LocalDate.of(2026, 1, 31)
        vm.onPurchasedOn(bought)
        vm.setExpiryInMonths(1)

        assertEquals(LocalDate.of(2026, 2, 28), vm.uiState.value.expiresOn)
    }

    @Test
    fun `the form will not save without a name or with a zero count`() {
        val vm = ItemEditViewModel(repository, 0)
        assertFalse(vm.uiState.value.canSave)

        vm.onName("Milk")
        assertTrue(vm.uiState.value.canSave)

        vm.onQuantity("0")
        assertFalse(vm.uiState.value.canSave)
        assertTrue(vm.uiState.value.quantityError)
    }
}
