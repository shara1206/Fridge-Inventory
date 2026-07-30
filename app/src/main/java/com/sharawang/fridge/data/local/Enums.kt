package com.sharawang.fridge.data.local

import androidx.annotation.StringRes
import com.sharawang.fridge.R

/**
 * Where the item physically lives. Orthogonal to [FoodCategory] on purpose: frozen
 * dumplings and frozen peas share a freezer but not a shopping aisle.
 */
enum class StorageArea(@StringRes val labelRes: Int) {
    FRIDGE(R.string.area_fridge),
    FREEZER(R.string.area_freezer),
    PANTRY(R.string.area_pantry)
}

/**
 * Shopping-aisle categories, not food-science ones — the split that matters is the one you
 * use when deciding what to cook or what to restock.
 *
 * Labels are string resources so the app reads naturally in both English and Chinese; the
 * enum names are the stable database values and must not be renamed casually (they are
 * persisted by name).
 */
enum class FoodCategory(@StringRes val labelRes: Int) {
    VEGETABLES(R.string.cat_vegetables),
    FRUIT(R.string.cat_fruit),
    MEAT(R.string.cat_meat),
    SEAFOOD(R.string.cat_seafood),
    TOFU_SOY(R.string.cat_tofu_soy),
    DAIRY_EGGS(R.string.cat_dairy_eggs),
    FROZEN_STAPLES(R.string.cat_frozen_staples),
    GRAINS_NOODLES(R.string.cat_grains_noodles),
    SEASONING(R.string.cat_seasoning),
    SNACKS(R.string.cat_snacks),
    BEVERAGE(R.string.cat_beverage),
    PREPARED(R.string.cat_prepared),
    OTHER(R.string.cat_other)
}

/**
 * Why an item left the kitchen. Without this a "waste report" can only count things that
 * disappeared, which says nothing about waste.
 */
enum class FinishReason(@StringRes val labelRes: Int) {
    USED(R.string.reason_used),
    DISCARDED(R.string.reason_discarded)
}

/** Store names are proper nouns, so they are not translated. */
enum class Store(val label: String) {
    HMART("H-Mart"),
    TT("T&T Supermarket"),
    TRADER_JOES("Trader Joe's"),
    WHOLE_FOODS("Whole Foods"),
    OTHER("Other")
}
