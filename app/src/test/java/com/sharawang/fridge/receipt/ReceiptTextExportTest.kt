package com.sharawang.fridge.receipt

import com.sharawang.fridge.data.local.Purchase
import com.sharawang.fridge.data.local.Store
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptTextExportTest {

    @Test
    fun `a receipt keeps its OCR text exactly, under a header naming the parser`() {
        val text = ReceiptTextExport.receipt(
            rawText = "KIRKLAND EGGS   5.99\nTOTAL  5.99\n",
            store = Store.OTHER,
            purchasedOn = LocalDate.of(2026, 11, 1),
            id = 12
        )

        assertEquals(
            "=== #12 · parser: Other · 2026-11-01 ===\n" +
                "KIRKLAND EGGS   5.99\nTOTAL  5.99\n" +
                "=== end ===",
            text
        )
    }

    @Test
    fun `an unreadable date and an empty scan still say so`() {
        val text = ReceiptTextExport.receipt(
            rawText = "  \n",
            store = Store.HMART,
            purchasedOn = null
        )

        assertEquals(
            "=== parser: H-Mart · date: unknown ===\n(OCR found no text)\n=== end ===",
            text
        )
    }

    @Test
    fun `several receipts are separated by a blank line, in the order given`() {
        val text = ReceiptTextExport.format(
            listOf(
                Purchase(
                    id = 2,
                    store = Store.TT,
                    purchasedOn = LocalDate.of(2026, 11, 2),
                    rawText = "B"
                ),
                Purchase(
                    id = 1,
                    store = Store.HMART,
                    purchasedOn = LocalDate.of(2026, 11, 1),
                    rawText = "A"
                )
            )
        )

        assertEquals(
            "=== #2 · parser: T&T Supermarket · 2026-11-02 ===\nB\n=== end ===\n\n" +
                "=== #1 · parser: H-Mart · 2026-11-01 ===\nA\n=== end ===",
            text
        )
    }
}
