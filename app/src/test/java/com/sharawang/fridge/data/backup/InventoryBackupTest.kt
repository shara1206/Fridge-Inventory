package com.sharawang.fridge.data.backup

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryBackupTest {

    private val today = LocalDate.of(2026, 7, 30)

    private val items = listOf(
        FoodItem(
            id = 7,
            name = "Bok Choy",
            category = FoodCategory.VEGETABLES,
            storageArea = StorageArea.FRIDGE,
            quantity = 2.0,
            unit = "bunch",
            purchasedOn = today.minusDays(2),
            expiresOn = today.plusDays(3),
            store = Store.TT,
            priceCents = 349,
            notes = "for soup"
        ),
        FoodItem(
            id = 8,
            name = "Milk",
            purchasedOn = today.minusDays(9),
            expiresOn = null,
            finishedOn = today.minusDays(1),
            finishedReason = FinishReason.DISCARDED
        )
    )

    @Test
    fun `a backup survives a round trip with every field intact`() {
        val restored = InventoryBackup.decode(InventoryBackup.encode(items, today))

        assertEquals(2, restored.size)
        val bokChoy = restored.first()
        assertEquals("Bok Choy", bokChoy.name)
        assertEquals(FoodCategory.VEGETABLES, bokChoy.category)
        assertEquals(StorageArea.FRIDGE, bokChoy.storageArea)
        assertEquals(2.0, bokChoy.quantity, 0.0001)
        assertEquals("bunch", bokChoy.unit)
        assertEquals(today.minusDays(2), bokChoy.purchasedOn)
        assertEquals(today.plusDays(3), bokChoy.expiresOn)
        assertEquals(Store.TT, bokChoy.store)
        assertEquals(349, bokChoy.priceCents)
        assertEquals("for soup", bokChoy.notes)
    }

    @Test
    fun `history keeps how and when the food left`() {
        val milk = InventoryBackup.decode(InventoryBackup.encode(items, today))[1]

        assertEquals(today.minusDays(1), milk.finishedOn)
        assertEquals(FinishReason.DISCARDED, milk.finishedReason)
        assertNull(milk.expiresOn)
    }

    @Test
    fun `ids are dropped so importing cannot collide with rows already here`() {
        assertTrue(
            InventoryBackup.decode(InventoryBackup.encode(items, today)).all { it.id == 0L }
        )
    }

    @Test(expected = BackupFormatException::class)
    fun `a file that is not a backup is rejected rather than half-read`() {
        InventoryBackup.decode("""{"format":"something-else","items":[]}""")
    }

    @Test(expected = BackupFormatException::class)
    fun `garbage is rejected`() {
        InventoryBackup.decode("not json at all")
    }

    @Test(expected = BackupFormatException::class)
    fun `a backup from a newer app version is refused instead of silently truncated`() {
        InventoryBackup.decode(
            """{"format":"${InventoryBackup.FORMAT}","version":99,"items":[]}"""
        )
    }

    @Test
    fun `an unknown category degrades to Other instead of losing the row`() {
        val json = """
            {
              "format": "${InventoryBackup.FORMAT}",
              "version": 1,
              "items": [
                { "name": "Mystery", "category": "SPACE_FOOD", "purchasedOn": "2026-07-30" }
              ]
            }
        """.trimIndent()

        val restored = InventoryBackup.decode(json).single()
        assertEquals("Mystery", restored.name)
        assertEquals(FoodCategory.OTHER, restored.category)
        assertEquals(1.0, restored.quantity, 0.0001)
    }

    @Test
    fun `nameless rows are skipped rather than importing blanks`() {
        val json = """
            {
              "format": "${InventoryBackup.FORMAT}",
              "version": 1,
              "items": [{ "name": "  " }, { "name": "Tofu" }]
            }
        """.trimIndent()

        assertEquals(listOf("Tofu"), InventoryBackup.decode(json).map { it.name })
    }
}
