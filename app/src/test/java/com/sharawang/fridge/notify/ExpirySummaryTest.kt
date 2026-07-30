package com.sharawang.fridge.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpirySummaryTest {

    private val today = LocalDate.of(2026, 7, 30)

    private fun entry(name: String, daysFromToday: Long) =
        ExpirySummary.Entry(name, today.plusDays(daysFromToday))

    @Test
    fun `nothing due means no notification at all`() {
        assertNull(
            ExpirySummary.build(
                entries = listOf(entry("Jasmine Rice", 300), entry("Sesame Oil", 200)),
                today = today,
                leadDays = 2
            )
        )
    }

    @Test
    fun `empty inventory means no notification`() {
        assertNull(ExpirySummary.build(emptyList(), today, leadDays = 2))
    }

    @Test
    fun `counts expired and due soon separately`() {
        val summary = ExpirySummary.build(
            entries = listOf(
                entry("Bok Choy", -3),
                entry("Pork Belly", -1),
                entry("Spinach", 1),
                entry("Milk", 2)
            ),
            today = today,
            leadDays = 2
        )!!
        assertEquals(2, summary.expiredCount)
        assertEquals(2, summary.dueSoonCount)
        assertEquals(4, summary.itemCount)
    }

    @Test
    fun `today counts as due soon, not expired`() {
        val summary = ExpirySummary.build(listOf(entry("Milk", 0)), today, leadDays = 2)!!
        assertEquals(0, summary.expiredCount)
        assertEquals(1, summary.dueSoonCount)
        assertEquals(0L, summary.shown.single().daysLeft)
    }

    @Test
    fun `soonest first`() {
        val summary = ExpirySummary.build(
            entries = listOf(
                entry("Milk", 2),
                entry("Bok Choy", -2),
                entry("Spinach", 0),
                entry("Tofu", 1)
            ),
            today = today,
            leadDays = 2
        )!!
        assertEquals(
            listOf("Bok Choy", "Spinach", "Tofu", "Milk"),
            summary.shown.map { it.name }
        )
        assertEquals(listOf(-2L, 0L, 1L, 2L), summary.shown.map { it.daysLeft })
    }

    @Test
    fun `caps the name list and reports how many are hidden`() {
        val summary = ExpirySummary.build(
            entries = (1..7).map { entry("Item $it", 0) },
            today = today,
            leadDays = 2
        )!!
        assertEquals(7, summary.itemCount)
        assertEquals(ExpirySummary.MAX_NAMES, summary.shown.size)
        assertEquals(3, summary.hiddenCount)
    }

    @Test
    fun `nothing hidden when the list fits`() {
        val summary = ExpirySummary.build(
            entries = listOf(entry("Milk", 0), entry("Tofu", 1)),
            today = today,
            leadDays = 2
        )!!
        assertEquals(0, summary.hiddenCount)
    }

    @Test
    fun `lead days decides what counts as soon`() {
        val entries = listOf(entry("Milk", 5))
        assertNull(ExpirySummary.build(entries, today, leadDays = 2))
        assertEquals(1, ExpirySummary.build(entries, today, leadDays = 5)!!.itemCount)
    }
}
