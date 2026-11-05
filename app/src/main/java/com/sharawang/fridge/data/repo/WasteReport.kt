package com.sharawang.fridge.data.repo

import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.Store
import kotlin.math.roundToInt

/**
 * What left the kitchen over a window, split by whether it was eaten or binned.
 *
 * Measured in quantity rather than rows. Counting rows made half a pack of pork thrown out
 * weigh exactly as much as a whole one, and — worse — could not see partial use at all,
 * since a row only ever counted once, on the day it emptied.
 *
 * Pure data + pure aggregation so it can be unit tested without Room.
 */
data class WasteReport(
    val usedQuantity: Double = 0.0,
    val discardedQuantity: Double = 0.0,
    /** Carried over from before the app recorded a reason. Never counted as either. */
    val unknownQuantity: Double = 0.0,
    val discardedCents: Int = 0,
    val discardedByCategory: List<Pair<FoodCategory, Double>> = emptyList(),
    val discardedByStore: List<Pair<Store, Double>> = emptyList()
) {
    val totalQuantity: Double get() = usedQuantity + discardedQuantity + unknownQuantity

    /** 0..100. Null when nothing is known, so the UI can say "no data" not "0%". */
    val wastePercent: Int?
        get() {
            val known = usedQuantity + discardedQuantity
            return if (known <= 0.0001) null else (discardedQuantity * 100 / known).roundToInt()
        }

    companion object {
        fun from(events: List<ConsumptionEvent>): WasteReport {
            val discarded = events.filter { it.reason == FinishReason.DISCARDED }
            return WasteReport(
                usedQuantity = events
                    .filter { it.reason == FinishReason.USED }
                    .sumOf { it.quantity },
                discardedQuantity = discarded.sumOf { it.quantity },
                unknownQuantity = events.filter { it.reason == null }.sumOf { it.quantity },
                discardedCents = discarded.sumOf { it.valueCents ?: 0 },
                discardedByCategory = discarded
                    .groupBy { it.category }
                    .map { (category, rows) -> category to rows.sumOf { it.quantity } }
                    .sortedWith(
                        compareByDescending<Pair<FoodCategory, Double>> { it.second }
                            .thenBy { it.first.ordinal }
                    ),
                discardedByStore = discarded
                    .groupBy { it.store }
                    .map { (store, rows) -> store to rows.sumOf { it.quantity } }
                    .sortedWith(
                        compareByDescending<Pair<Store, Double>> { it.second }
                            .thenBy { it.first.ordinal }
                    )
            )
        }
    }
}
