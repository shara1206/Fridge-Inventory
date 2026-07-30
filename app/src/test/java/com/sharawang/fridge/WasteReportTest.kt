package com.sharawang.fridge

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.repo.WasteReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WasteReportTest {

    private fun finished(
        name: String,
        reason: FinishReason?,
        category: FoodCategory = FoodCategory.VEGETABLES,
        store: Store = Store.HMART,
        priceCents: Int? = null
    ) = FoodItem(
        id = name.hashCode().toLong(),
        name = name,
        category = category,
        store = store,
        priceCents = priceCents,
        finishedOn = LocalDate.of(2026, 7, 20),
        finishedReason = reason
    )

    @Test
    fun `empty history reports nothing rather than zero percent`() {
        val report = WasteReport.from(emptyList())
        assertEquals(0, report.finishedCount)
        assertNull(report.wastePercent)
    }

    @Test
    fun `splits eaten from thrown out`() {
        val report = WasteReport.from(
            listOf(
                finished("Spinach", FinishReason.DISCARDED),
                finished("Milk", FinishReason.USED),
                finished("Tofu", FinishReason.USED),
                finished("Pork", FinishReason.USED)
            )
        )
        assertEquals(3, report.usedCount)
        assertEquals(1, report.discardedCount)
        assertEquals(25, report.wastePercent)
    }

    @Test
    fun `items finished before the reason column existed are not counted as waste`() {
        val report = WasteReport.from(
            listOf(
                finished("Legacy row", null),
                finished("Spinach", FinishReason.DISCARDED),
                finished("Milk", FinishReason.USED)
            )
        )
        assertEquals(1, report.unknownCount)
        assertEquals(3, report.finishedCount)
        // Percent uses only rows with a known reason, so the unknown one cannot skew it.
        assertEquals(50, report.wastePercent)
    }

    @Test
    fun `sums the money thrown away, ignoring unpriced rows`() {
        val report = WasteReport.from(
            listOf(
                finished("Spinach", FinishReason.DISCARDED, priceCents = 499),
                finished("Bok Choy", FinishReason.DISCARDED, priceCents = 329),
                finished("Herbs", FinishReason.DISCARDED, priceCents = null),
                finished("Milk", FinishReason.USED, priceCents = 899)
            )
        )
        assertEquals(828, report.discardedCents)
    }

    @Test
    fun `ranks wasted categories by count, then by enum order`() {
        val report = WasteReport.from(
            listOf(
                finished("Spinach", FinishReason.DISCARDED, FoodCategory.VEGETABLES),
                finished("Bok Choy", FinishReason.DISCARDED, FoodCategory.VEGETABLES),
                finished("Salmon", FinishReason.DISCARDED, FoodCategory.SEAFOOD),
                finished("Apple", FinishReason.DISCARDED, FoodCategory.FRUIT),
                finished("Milk", FinishReason.USED, FoodCategory.DAIRY_EGGS)
            )
        )
        assertEquals(
            listOf(
                FoodCategory.VEGETABLES to 2,
                FoodCategory.FRUIT to 1,
                FoodCategory.SEAFOOD to 1
            ),
            report.discardedByCategory
        )
    }

    @Test
    fun `groups waste by store`() {
        val report = WasteReport.from(
            listOf(
                finished("A", FinishReason.DISCARDED, store = Store.TT),
                finished("B", FinishReason.DISCARDED, store = Store.TT),
                finished("C", FinishReason.DISCARDED, store = Store.HMART)
            )
        )
        assertEquals(listOf(Store.TT to 2, Store.HMART to 1), report.discardedByStore)
    }
}
