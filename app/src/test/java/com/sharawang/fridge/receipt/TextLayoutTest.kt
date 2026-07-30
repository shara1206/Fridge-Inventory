package com.sharawang.fridge.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutTest {

    private fun fragment(text: String, left: Int, top: Int) =
        TextLayout.Fragment(text, left = left, top = top, bottom = top + 20)

    @Test
    fun `stitches name and price columns into one row`() {
        val rows = TextLayout.toRows(
            listOf(
                fragment("4.99", left = 400, top = 100),
                fragment("ORG SPNCH", left = 20, top = 102),
                fragment("TOTAL", left = 20, top = 160),
                fragment("4.99", left = 400, top = 158)
            )
        )
        assertEquals(listOf("ORG SPNCH  4.99", "TOTAL  4.99"), rows)
    }

    @Test
    fun `keeps distinct rows separate`() {
        val rows = TextLayout.toRows(
            listOf(
                fragment("MILK", left = 20, top = 0),
                fragment("EGGS", left = 20, top = 40),
                fragment("BREAD", left = 20, top = 80)
            )
        )
        assertEquals(listOf("MILK", "EGGS", "BREAD"), rows)
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<String>(), TextLayout.toRows(emptyList()))
    }
}
