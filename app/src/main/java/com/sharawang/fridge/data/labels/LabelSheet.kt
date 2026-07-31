package com.sharawang.fridge.data.labels

import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.isFinished

/**
 * One card on the label sheet: everything that lives in one place under one heading.
 *
 * A zone can be empty and still be worth printing — the ten headings on the sheet are
 * fixed, and an empty card is a place to write in what gets put there next.
 */
data class LabelZone(
    val category: FoodCategory,
    val storageArea: StorageArea,
    val items: List<FoodItem>,
    /** True for the ten headings the sheet always carries, whatever is in the kitchen. */
    val printed: Boolean
)

/**
 * The sheet, in print order.
 *
 * The ten printed zones come first and never move, because the point of the sheet is that
 * the card taped to a shelf stays the card for that shelf. Anything the ten do not cover —
 * drinks, grains, the pantry — becomes an extra card after them, titled as it goes.
 */
data class LabelSheet(val zones: List<LabelZone>) {

    val printed: List<LabelZone> get() = zones.take(PRINTED_ZONES.size)

    /** Zones the printed ten do not cover. Usually empty. */
    val extra: List<LabelZone> get() = zones.drop(PRINTED_ZONES.size)

    val itemCount: Int get() = zones.sumOf { it.items.size }

    /** Extra cards spill onto further sheets of paper, ten to a page. */
    val pageCount: Int get() = 1 + (extra.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE

    companion object {
        const val CARDS_PER_PAGE = 10

        /**
         * The ten fixed cards, in reading order.
         *
         * Slot four was 酱料 · 调味 on the first printing, but that shelf stays empty and
         * tofu does not: seasoning moved to an extra card and tofu took the slot. The card
         * keeps the colour it was printed in, so an old sheet and a new one still read as
         * the same fridge.
         */
        val PRINTED_ZONES: List<Pair<FoodCategory, StorageArea>> = listOf(
            FoodCategory.DAIRY_EGGS to StorageArea.FRIDGE,
            FoodCategory.VEGETABLES to StorageArea.FRIDGE,
            FoodCategory.FRUIT to StorageArea.FRIDGE,
            FoodCategory.TOFU_SOY to StorageArea.FRIDGE,
            FoodCategory.PREPARED to StorageArea.FRIDGE,
            FoodCategory.PREPARED to StorageArea.FREEZER,
            FoodCategory.MEAT to StorageArea.FREEZER,
            FoodCategory.SEAFOOD to StorageArea.FREEZER,
            FoodCategory.SNACKS to StorageArea.FREEZER,
            FoodCategory.FROZEN_STAPLES to StorageArea.FREEZER
        )

        private val PRINTED_KEYS = PRINTED_ZONES.toSet()

        /**
         * Finished rows are history, not contents. A label answers "what is in there now",
         * so anything already eaten or thrown out is left off rather than printed and
         * crossed out.
         */
        fun from(items: List<FoodItem>): LabelSheet {
            val byZone = items.filterNot { it.isFinished }
                .groupBy { it.category to it.storageArea }

            val printed = PRINTED_ZONES.map { (category, area) ->
                LabelZone(category, area, byZone[category to area].orEmpty(), printed = true)
            }
            val extra = byZone.keys
                .filterNot { it in PRINTED_KEYS }
                // Grouped by where it lives first: the extras get taped up together, and
                // sorting by category would scatter the freezer cards among the fridge ones.
                .sortedWith(compareBy({ it.second.ordinal }, { it.first.ordinal }))
                .map { key -> LabelZone(key.first, key.second, byZone.getValue(key), printed = false) }

            return LabelSheet(printed + extra)
        }
    }
}

/**
 * How one item reads on a card.
 *
 * A count of one is left off: the name already says there is one, and "×1" on every line
 * turns a list you can read across the kitchen into a table you have to study.
 */
fun FoodItem.labelLine(): String =
    if (quantity > 1.0) "$name ×${trimQuantity(quantity)}" else name

private fun trimQuantity(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) {
        quantity.toLong().toString()
    } else {
        quantity.toString()
    }
