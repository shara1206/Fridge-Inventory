package com.sharawang.fridge.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.receipt.ParsedLine
import com.sharawang.fridge.receipt.ParsedReceipt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: InventoryRepository

    private val today = LocalDate.of(2026, 7, 30)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = InventoryRepository(database.foodItemDao(), database.purchaseDao())
    }

    @After
    fun tearDown() = database.close()

    // ---- merging ------------------------------------------------------------

    @Test
    fun `a repeat purchase adds quantity instead of a second row`() = runTest {
        val first = repository.draftFor("Baby Spinach", purchasedOn = today.minusDays(2))
        repository.addOrMerge(first)

        val outcome = repository.addOrMerge(
            repository.draftFor("baby spinach", purchasedOn = today)
        )

        assertTrue(outcome is MergeOutcome.Merged)
        val items = repository.activeItems().first()
        assertEquals(1, items.size)
        assertEquals(2.0, items.single().quantity, 0.0001)
    }

    @Test
    fun `merging keeps the earlier expiry so the older stock still gets flagged`() = runTest {
        val older = repository.draftFor("Bok Choy", purchasedOn = today.minusDays(3))
            .copy(expiresOn = today.plusDays(1))
        repository.addOrMerge(older)
        repository.addOrMerge(
            repository.draftFor("Bok Choy", purchasedOn = today)
                .copy(expiresOn = today.plusDays(6))
        )

        assertEquals(today.plusDays(1), repository.activeItems().first().single().expiresOn)
    }

    @Test
    fun `a draft leaves the expiry unset rather than guessing a date`() {
        assertNull(repository.draftFor("Baby Spinach", purchasedOn = today).expiresOn)
    }

    @Test
    fun `an expiry set by hand survives a merge with an undated repeat purchase`() = runTest {
        repository.addOrMerge(
            repository.draftFor("Milk", purchasedOn = today).copy(expiresOn = today.plusDays(5))
        )
        repository.addOrMerge(repository.draftFor("Milk", purchasedOn = today))

        assertEquals(today.plusDays(5), repository.activeItems().first().single().expiresOn)
    }

    // ---- the list's +/− are events, not edits -------------------------------

    @Test
    fun `buying another one moves the purchase date but leaves the expiry alone`() = runTest {
        val id = repository.save(
            repository.draftFor("Yogurt", purchasedOn = today.minusDays(4))
                .copy(quantity = 2.0, expiresOn = today.plusDays(3))
        ).id

        repository.addOne(repository.item(id)!!, on = today)

        val item = repository.item(id)!!
        assertEquals(3.0, item.quantity, 0.0001)
        assertEquals(today, item.purchasedOn)
        assertEquals(today.plusDays(3), item.expiresOn)
    }

    @Test
    fun `eating one of several just lowers the count`() = runTest {
        val id = repository.save(repository.draftFor("Eggs").copy(quantity = 3.0)).id

        repository.useAmount(repository.item(id)!!, 1.0)

        val item = repository.item(id)!!
        assertEquals(2.0, item.quantity, 0.0001)
        assertNull(item.finishedOn)
    }

    @Test
    fun `eating the last one finishes the row as eaten, not as waste`() = runTest {
        val id = repository.save(repository.draftFor("Eggs").copy(quantity = 1.0)).id

        repository.useAmount(repository.item(id)!!, 1.0)

        assertEquals(FinishReason.USED, repository.item(id)!!.finishedReason)
        assertTrue(repository.activeItems().first().isEmpty())
    }

    @Test
    fun `merging sums price and keeps the latest purchase date`() = runTest {
        repository.addOrMerge(
            repository.draftFor("Tofu", purchasedOn = today.minusDays(1), priceCents = 229)
        )
        repository.addOrMerge(
            repository.draftFor("Tofu", purchasedOn = today, priceCents = 199)
        )

        val item = repository.activeItems().first().single()
        assertEquals(428, item.priceCents)
        assertEquals(today, item.purchasedOn)
    }

    @Test
    fun `the same name in a different storage area stays a separate item`() = runTest {
        repository.addOrMerge(
            repository.draftFor("Peas").copy(storageArea = StorageArea.FREEZER)
        )
        repository.addOrMerge(
            repository.draftFor("Peas").copy(storageArea = StorageArea.FRIDGE)
        )

        assertEquals(2, repository.activeItems().first().size)
    }

    // ---- partial use --------------------------------------------------------

    @Test
    fun `using part of an item leaves the rest behind`() = runTest {
        val id = repository.save(repository.draftFor("Ground Pork").copy(quantity = 2.0)).id
        val item = repository.item(id)!!

        repository.useAmount(item, 0.5)

        assertEquals(1.5, repository.item(id)!!.quantity, 0.0001)
        assertNull(repository.item(id)!!.finishedOn)
    }

    @Test
    fun `using the last of it finishes the row as eaten, not as waste`() = runTest {
        val id = repository.save(repository.draftFor("Milk").copy(quantity = 1.0)).id

        repository.useAmount(repository.item(id)!!, 1.0)

        val finished = repository.item(id)!!
        assertEquals(FinishReason.USED, finished.finishedReason)
        assertTrue(repository.activeItems().first().isEmpty())
    }

    @Test
    fun `overshooting the quantity does not leave a negative ghost`() = runTest {
        val id = repository.save(repository.draftFor("Herbs").copy(quantity = 1.0)).id

        repository.useAmount(repository.item(id)!!, 5.0)

        assertEquals(FinishReason.USED, repository.item(id)!!.finishedReason)
    }

    // ---- receipts -----------------------------------------------------------

    private fun receipt(vararg names: String) = ParsedReceipt(
        store = Store.TT,
        purchasedOn = today,
        totalCents = 1000,
        lines = names.map { ParsedLine(name = it, priceCents = 100, rawLine = "$it 1.00") },
        rawText = names.joinToString("\n")
    )

    @Test
    fun `committing a receipt records every line, accepted or not`() = runTest {
        val parsed = receipt("Bok Choy", "Pork Belly", "Mystery Junk")
        val kept = parsed.lines.take(2).map { ReceiptEntry(it, it.name) }

        val purchaseId = repository.commitReceipt(parsed, kept)

        val lines = database.purchaseDao().linesFor(purchaseId)
        assertEquals(3, lines.size)
        assertEquals(2, lines.count { it.accepted })
        assertEquals(2, repository.activeItems().first().size)
    }

    @Test
    fun `a per-line storage override survives into the saved item`() = runTest {
        val parsed = receipt("Dumplings")
        val kept = listOf(
            ReceiptEntry(parsed.lines.single(), "Dumplings", StorageArea.PANTRY)
        )

        repository.commitReceipt(parsed, kept)

        assertEquals(
            StorageArea.PANTRY,
            repository.activeItems().first().single().storageArea
        )
    }

    @Test
    fun `a receipt line merges into what is already in the kitchen`() = runTest {
        repository.addOrMerge(repository.draftFor("Bok Choy", purchasedOn = today.minusDays(1)))
        val parsed = receipt("Bok Choy")

        repository.commitReceipt(parsed, listOf(ReceiptEntry(parsed.lines.single(), "Bok Choy")))

        val items = repository.activeItems().first()
        assertEquals(1, items.size)
        assertEquals(2.0, items.single().quantity, 0.0001)
    }

    // ---- waste report -------------------------------------------------------

    @Test
    fun `the waste report reads from what was actually finished`() = runTest {
        val eaten = repository.save(repository.draftFor("Milk")).id
        val binned = repository.save(
            repository.draftFor("Spinach", priceCents = 499)
                .copy(category = FoodCategory.VEGETABLES)
        ).id
        repository.markFinished(eaten, FinishReason.USED)
        repository.markFinished(binned, FinishReason.DISCARDED)

        val report = repository.wasteReport().first()
        assertEquals(1, report.usedCount)
        assertEquals(1, report.discardedCount)
        assertEquals(499, report.discardedCents)
        assertEquals(50, report.wastePercent)
    }
}
