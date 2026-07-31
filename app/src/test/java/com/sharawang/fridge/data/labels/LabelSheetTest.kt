package com.sharawang.fridge.data.labels

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM: [LabelSheet] deliberately knows nothing about Android, because deciding which
 * card a jar of tofu belongs on is the part worth testing and it should not need an emulator.
 */
class LabelSheetTest {

    private val today = LocalDate.of(2026, 7, 31)

    private fun item(
        name: String,
        category: FoodCategory,
        area: StorageArea = StorageArea.FRIDGE,
        quantity: Double = 1.0,
        finished: Boolean = false
    ) = FoodItem(
        name = name,
        category = category,
        storageArea = area,
        quantity = quantity,
        purchasedOn = today,
        finishedOn = if (finished) today else null,
        finishedReason = if (finished) FinishReason.USED else null
    )

    @Test
    fun theTenPrintedCardsAlwaysExistEvenForAnEmptyKitchen() {
        val sheet = LabelSheet.from(emptyList())

        assertEquals(LabelSheet.PRINTED_ZONES.size, sheet.zones.size)
        assertTrue(sheet.zones.all { it.printed && it.items.isEmpty() })
        assertEquals(1, sheet.pageCount)
    }

    @Test
    fun itemsLandOnTheCardForTheirCategoryAndArea() {
        val sheet = LabelSheet.from(
            listOf(
                item("Ribeye", FoodCategory.MEAT, StorageArea.FREEZER),
                item("Dumplings", FoodCategory.PREPARED, StorageArea.FREEZER),
                item("Century egg", FoodCategory.PREPARED, StorageArea.FRIDGE)
            )
        )

        val freezerPrepared = sheet.zones.single {
            it.category == FoodCategory.PREPARED && it.storageArea == StorageArea.FREEZER
        }
        assertEquals(listOf("Dumplings"), freezerPrepared.items.map { it.name })
        // The same category in a different area is a different card, which is the whole
        // reason the two are keyed together.
        val fridgePrepared = sheet.zones.single {
            it.category == FoodCategory.PREPARED && it.storageArea == StorageArea.FRIDGE
        }
        assertEquals(listOf("Century egg"), fridgePrepared.items.map { it.name })
    }

    @Test
    fun tofuHasItsOwnPrintedCardAndSeasoningDoesNot() {
        val sheet = LabelSheet.from(
            listOf(
                item("Firm tofu", FoodCategory.TOFU_SOY),
                item("Doubanjiang", FoodCategory.SEASONING)
            )
        )

        assertTrue(sheet.printed.single { it.category == FoodCategory.TOFU_SOY }.items.isNotEmpty())
        assertEquals(
            listOf(FoodCategory.SEASONING),
            sheet.extra.map { it.category }
        )
    }

    @Test
    fun uncoveredCategoriesBecomeExtraCardsGroupedByArea() {
        val sheet = LabelSheet.from(
            listOf(
                item("Rice", FoodCategory.GRAINS_NOODLES, StorageArea.PANTRY),
                item("Oolong", FoodCategory.BEVERAGE, StorageArea.FRIDGE),
                item("Sparkling water", FoodCategory.BEVERAGE, StorageArea.PANTRY)
            )
        )

        assertEquals(
            listOf(
                FoodCategory.BEVERAGE to StorageArea.FRIDGE,
                FoodCategory.GRAINS_NOODLES to StorageArea.PANTRY,
                FoodCategory.BEVERAGE to StorageArea.PANTRY
            ),
            sheet.extra.map { it.category to it.storageArea }
        )
        assertTrue(sheet.extra.none { it.printed })
        assertEquals(3, sheet.itemCount)
    }

    @Test
    fun finishedItemsAreLeftOff() {
        val sheet = LabelSheet.from(
            listOf(
                item("Spinach", FoodCategory.VEGETABLES),
                item("Old spinach", FoodCategory.VEGETABLES, finished = true)
            )
        )

        assertEquals(listOf("Spinach"), sheet.zones.flatMap { it.items }.map { it.name })
        assertEquals(1, sheet.itemCount)
    }

    @Test
    fun extraCardsSpillOntoFurtherPagesTenAtATime() {
        // Nothing in the pantry is covered by the printed ten, so every category spills.
        val sheet = LabelSheet.from(
            FoodCategory.entries.map { item("x-${it.name}", it, StorageArea.PANTRY) }
        )

        assertEquals(FoodCategory.entries.size, sheet.extra.size)
        assertEquals(1 + 2, sheet.pageCount) // 13 extras: one full page plus a part page
        assertFalse(sheet.printed.any { it.items.isNotEmpty() })
    }

    @Test
    fun aCountOfOneIsLeftOffTheLine() {
        assertEquals("Milk", item("Milk", FoodCategory.DAIRY_EGGS).labelLine())
        assertEquals("Milk ×2", item("Milk", FoodCategory.DAIRY_EGGS, quantity = 2.0).labelLine())
        // Half a bag of chicken is a real quantity and has to survive the formatting.
        assertEquals(
            "Chicken ×1.5",
            item("Chicken", FoodCategory.MEAT, quantity = 1.5).labelLine()
        )
    }
}
