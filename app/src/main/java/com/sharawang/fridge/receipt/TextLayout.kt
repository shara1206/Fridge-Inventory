package com.sharawang.fridge.receipt

import kotlin.math.abs

/**
 * OCR returns fragments, not receipt rows: a product name and its price usually come back
 * as two separate fragments because a wide gap separates the columns. Parsing works far
 * better when fragments on the same physical row are stitched back together first.
 *
 * Deliberately free of Android types so it can be unit tested on the JVM.
 */
object TextLayout {

    data class Fragment(
        val text: String,
        val left: Int,
        val top: Int,
        val bottom: Int
    ) {
        val centerY: Int get() = (top + bottom) / 2
        val height: Int get() = (bottom - top).coerceAtLeast(1)
    }

    /**
     * Groups fragments into rows by vertical proximity, orders each row left to right and
     * returns one string per row.
     */
    fun toRows(fragments: List<Fragment>): List<String> {
        val usable = fragments.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return emptyList()

        val medianHeight = usable.map { it.height }.sorted()[usable.size / 2]
        val tolerance = (medianHeight * 0.6).toInt().coerceAtLeast(4)

        val rows = mutableListOf<MutableList<Fragment>>()
        for (fragment in usable.sortedBy { it.centerY }) {
            val current = rows.lastOrNull()
            if (current != null && abs(current.last().centerY - fragment.centerY) <= tolerance) {
                current.add(fragment)
            } else {
                rows.add(mutableListOf(fragment))
            }
        }

        return rows
            .map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() }.trim() }
            .filter { it.isNotEmpty() }
    }
}
