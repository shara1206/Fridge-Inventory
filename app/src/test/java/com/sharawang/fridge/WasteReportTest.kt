package com.sharawang.fridge

import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.repo.WasteReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WasteReportTest {

    private fun event(
        name: String,
        reason: FinishReason?,
        quantity: Double = 1.0,
        category: FoodCategory = FoodCategory.VEGETABLES,
        store: Store = Store.HMART,
        valueCents: Int? = null
    ) = ConsumptionEvent(
        id = name.hashCode().toLong(),
        itemId = null,
        name = name,
        category = category,
        store = store,
        quantity = quantity,
        reason = reason,
        occurredOn = LocalDate.of(2026, 7, 20),
        valueCents = valueCents
    )

    @Test
    fun `an empty log reports nothing rather than zero percent`() {
        val report = WasteReport.from(emptyList())
        assertEquals(0.0, report.totalQuantity, 0.0001)
        assertNull(report.wastePercent)
    }

    @Test
    fun `splits eaten from thrown out`() {
        val report = WasteReport.from(
            listOf(
                event("Spinach", FinishReason.DISCARDED),
                event("Milk", FinishReason.USED),
                event("Tofu", FinishReason.USED),
                event("Pork", FinishReason.USED)
            )
        )
        assertEquals(3.0, report.usedQuantity, 0.0001)
        assertEquals(1.0, report.discardedQuantity, 0.0001)
        assertEquals(25, report.wastePercent)
    }

    @Test
    fun `partial use counts for what it was, not as a whole item`() {
        val report = WasteReport.from(
            listOf(
                event("Ground Pork", FinishReason.USED, quantity = 0.5),
                event("Ground Pork", FinishReason.DISCARDED, quantity = 0.5)
            )
        )
        assertEquals(0.5, report.usedQuantity, 0.0001)
        assertEquals(0.5, report.discardedQuantity, 0.0001)
        assertEquals(50, report.wastePercent)
    }

    @Test
    fun `entries carried over from before the reason column are not counted as waste`() {
        val report = WasteReport.from(
            listOf(
                event("Legacy row", null),
                event("Spinach", FinishReason.DISCARDED),
                event("Milk", FinishReason.USED)
            )
        )
        assertEquals(1.0, report.unknownQuantity, 0.0001)
        assertEquals(3.0, report.totalQuantity, 0.0001)
        // Percent uses only entries with a known reason, so the unknown one cannot skew it.
        assertEquals(50, report.wastePercent)
    }

    @Test
    fun `sums the money thrown away, ignoring unpriced entries`() {
        val report = WasteReport.from(
            listOf(
                event("Spinach", FinishReason.DISCARDED, valueCents = 499),
                event("Bok Choy", FinishReason.DISCARDED, valueCents = 329),
                event("Herbs", FinishReason.DISCARDED, valueCents = null),
                event("Milk", FinishReason.USED, valueCents = 899)
            )
        )
        assertEquals(828, report.discardedCents)
    }

    @Test
    fun `ranks wasted categories by quantity, then by enum order`() {
        val report = WasteReport.from(
            listOf(
                event("Spinach", FinishReason.DISCARDED, category = FoodCategory.VEGETABLES),
                event("Bok Choy", FinishReason.DISCARDED, category = FoodCategory.VEGETABLES),
                event("Salmon", FinishReason.DISCARDED, category = FoodCategory.SEAFOOD),
                event("Apple", FinishReason.DISCARDED, category = FoodCategory.FRUIT),
                event("Milk", FinishReason.USED, category = FoodCategory.DAIRY_EGGS)
            )
        )
        assertEquals(
            listOf(
                FoodCategory.VEGETABLES to 2.0,
                FoodCategory.FRUIT to 1.0,
                FoodCategory.SEAFOOD to 1.0
            ),
            report.discardedByCategory
        )
    }

    @Test
    fun `groups waste by store`() {
        val report = WasteReport.from(
            listOf(
                event("A", FinishReason.DISCARDED, store = Store.TT),
                event("B", FinishReason.DISCARDED, store = Store.TT),
                event("C", FinishReason.DISCARDED, store = Store.HMART)
            )
        )
        assertEquals(listOf(Store.TT to 2.0, Store.HMART to 1.0), report.discardedByStore)
    }
}
