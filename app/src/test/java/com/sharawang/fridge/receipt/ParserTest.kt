package com.sharawang.fridge.receipt

import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.receipt.parser.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParserTest {

    private fun parse(raw: String): ParsedReceipt = Parsers.forText(raw).parse(raw)

    private fun names(receipt: ParsedReceipt) = receipt.lines.map { it.name }

    // ---- store detection ---------------------------------------------------

    @Test
    fun `detects each store`() {
        assertEquals(Store.HMART, parse(SampleReceipts.H_MART).store)
        assertEquals(Store.TT, parse(SampleReceipts.TT).store)
        assertEquals(Store.TRADER_JOES, parse(SampleReceipts.TRADER_JOES).store)
        assertEquals(Store.WHOLE_FOODS, parse(SampleReceipts.WHOLE_FOODS).store)
        assertEquals(Store.OTHER, parse(SampleReceipts.UNKNOWN_STORE).store)
    }

    // ---- noise rejection ---------------------------------------------------

    @Test
    fun `drops totals taxes and payment lines`() {
        val receipts = listOf(
            SampleReceipts.H_MART,
            SampleReceipts.TT,
            SampleReceipts.TRADER_JOES,
            SampleReceipts.WHOLE_FOODS,
            SampleReceipts.UNKNOWN_STORE
        ).map { parse(it) }

        val forbidden = listOf("total", "tax", "visa", "gst", "savings", "interac", "items")
        receipts.forEach { receipt ->
            names(receipt).forEach { name ->
                forbidden.forEach { word ->
                    assertTrue(
                        "parsed a noise line as an item: '$name'",
                        !name.lowercase().contains(word)
                    )
                }
            }
        }
    }

    // ---- H-Mart ------------------------------------------------------------

    @Test
    fun `hmart strips upc codes and expands abbreviations`() {
        val receipt = parse(SampleReceipts.H_MART)
        assertEquals(4, receipt.lines.size)
        assertTrue(names(receipt).contains("Organic Spinach 5oz"))
        assertTrue(names(receipt).contains("Pork Belly Slice"))
        assertTrue(names(receipt).contains("Napa Cabbage"))
    }

    @Test
    fun `hmart reads date and total`() {
        val receipt = parse(SampleReceipts.H_MART)
        assertEquals(LocalDate.of(2026, 4, 12), receipt.purchasedOn)
        assertEquals(2319, receipt.totalCents)
    }

    @Test
    fun `hmart applies weight line to the preceding item`() {
        val receipt = parse(SampleReceipts.H_MART)
        val porkBelly = receipt.lines.first { it.name.startsWith("Pork Belly") }
        assertEquals(0.86, porkBelly.quantity, 0.001)
        assertEquals(1245, porkBelly.priceCents)
    }

    // ---- T&T ---------------------------------------------------------------

    @Test
    fun `tt keeps chinese names and strips tax flags`() {
        val receipt = parse(SampleReceipts.TT)
        assertEquals(3, receipt.lines.size)
        assertTrue(names(receipt).any { it.contains("上海青") && it.contains("Bok Choy") })
        val bokChoy = receipt.lines.first { it.name.contains("上海青") }
        assertEquals(329, bokChoy.priceCents)
    }

    @Test
    fun `tt reads iso date and count multiplier`() {
        val receipt = parse(SampleReceipts.TT)
        assertEquals(LocalDate.of(2026, 4, 15), receipt.purchasedOn)
        val dumplings = receipt.lines.first { it.name.contains("Dumpling") }
        assertEquals(2.0, dumplings.quantity, 0.001)
    }

    // ---- Trader Joe's ------------------------------------------------------

    @Test
    fun `trader joes handles two digit year and quantity`() {
        val receipt = parse(SampleReceipts.TRADER_JOES)
        assertEquals(LocalDate.of(2026, 4, 20), receipt.purchasedOn)
        assertEquals(3, receipt.lines.size)
        val chicken = receipt.lines.first { it.name.contains("Chicken") }
        assertEquals(2.0, chicken.quantity, 0.001)
        assertTrue(names(receipt).any { it.startsWith("Unsweetened Vanilla") })
    }

    // ---- Whole Foods -------------------------------------------------------

    @Test
    fun `whole foods keeps 365 brand and drops prime savings`() {
        val receipt = parse(SampleReceipts.WHOLE_FOODS)
        assertEquals(3, receipt.lines.size)
        assertTrue(names(receipt).any { it.startsWith("365 Organic Whole Milk") })
        assertTrue(names(receipt).none { it.contains("Prime") })
        val chicken = receipt.lines.first { it.name.contains("Chicken") }
        assertEquals(1.42, chicken.quantity, 0.001)
    }

    // ---- fallback ----------------------------------------------------------

    @Test
    fun `generic parser still reads an unknown receipt`() {
        val receipt = parse(SampleReceipts.UNKNOWN_STORE)
        assertEquals(2, receipt.lines.size)
        assertEquals(467, receipt.totalCents)
        assertNotNull(receipt.purchasedOn)
    }

    @Test
    fun `blank text yields no lines`() {
        assertTrue(parse("").lines.isEmpty())
    }
}
