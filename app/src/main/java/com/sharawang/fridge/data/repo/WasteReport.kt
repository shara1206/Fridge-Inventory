package com.sharawang.fridge.data.repo

import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.Store

/**
 * What left the kitchen over a window, split by whether it was eaten or binned.
 *
 * Pure data + pure aggregation so it can be unit tested without Room.
 */
data class WasteReport(
    val usedCount: Int = 0,
    val discardedCount: Int = 0,
    val unknownCount: Int = 0,
    val discardedCents: Int = 0,
    val discardedByCategory: List<Pair<FoodCategory, Int>> = emptyList(),
    val discardedByStore: List<Pair<Store, Int>> = emptyList()
) {
    val finishedCount: Int get() = usedCount + discardedCount + unknownCount

    /** 0..100. Null when nothing was finished, so the UI can say "no data" not "0%". */
    val wastePercent: Int?
        get() {
            val known = usedCount + discardedCount
            return if (known == 0) null else (discardedCount * 100) / known
        }

    companion object {
        fun from(items: List<FoodItem>): WasteReport {
            val discarded = items.filter { it.finishedReason == FinishReason.DISCARDED }
            return WasteReport(
                usedCount = items.count { it.finishedReason == FinishReason.USED },
                discardedCount = discarded.size,
                unknownCount = items.count { it.finishedReason == null },
                discardedCents = discarded.sumOf { it.priceCents ?: 0 },
                discardedByCategory = discarded
                    .groupingBy { it.category }
                    .eachCount()
                    .toList()
                    .sortedWith(compareByDescending<Pair<FoodCategory, Int>> { it.second }
                        .thenBy { it.first.ordinal }),
                discardedByStore = discarded
                    .groupingBy { it.store }
                    .eachCount()
                    .toList()
                    .sortedWith(compareByDescending<Pair<Store, Int>> { it.second }
                        .thenBy { it.first.ordinal })
            )
        }
    }
}
