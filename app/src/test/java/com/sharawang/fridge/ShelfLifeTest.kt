package com.sharawang.fridge

import com.sharawang.fridge.data.ShelfLife
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.StorageArea
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfLifeTest {

    @Test
    fun `leafy greens get a short fridge life`() {
        val guess = ShelfLife.guess("Organic Baby Spinach")
        assertEquals(FoodCategory.VEGETABLES, guess.category)
        assertEquals(StorageArea.FRIDGE, guess.storageArea)
        assertEquals(5, guess.days)
    }

    @Test
    fun `frozen items go to the freezer`() {
        // Parsers expand receipt shorthand before this runs, so match on real words.
        val guess = ShelfLife.guess("Frozen Pork Dumplings")
        assertEquals(StorageArea.FREEZER, guess.storageArea)
        assertEquals(FoodCategory.FROZEN_STAPLES, guess.category)
    }

    @Test
    fun `longest keyword wins over a shorter overlapping one`() {
        // "cabbage" (sturdy produce, 12d) must beat "napa" (leafy, 5d)
        assertEquals(12, ShelfLife.guess("Napa Cabbage").days)
    }

    @Test
    fun `meat and seafood are separate categories with separate lives`() {
        val pork = ShelfLife.guess("Pork Belly Slice")
        assertEquals(FoodCategory.MEAT, pork.category)
        assertEquals(3, pork.days)

        val salmon = ShelfLife.guess("Atlantic Salmon Fillet")
        assertEquals(FoodCategory.SEAFOOD, salmon.category)
        assertEquals(2, salmon.days)
    }

    @Test
    fun `fruit and vegetables are separate categories`() {
        assertEquals(FoodCategory.FRUIT, ShelfLife.guess("Organic Strawberry 1lb").category)
        assertEquals(FoodCategory.VEGETABLES, ShelfLife.guess("Napa Cabbage").category)
    }

    @Test
    fun `tofu is not filed under dairy`() {
        val guess = ShelfLife.guess("Tofu Firm 19oz")
        assertEquals(FoodCategory.TOFU_SOY, guess.category)
        assertEquals(StorageArea.FRIDGE, guess.storageArea)
    }

    @Test
    fun `fermented sides keep for months, unlike deli food`() {
        assertEquals(90, ShelfLife.guess("Napa Kimchi 28oz").days)
        assertEquals(3, ShelfLife.guess("Rotisserie Chicken").days)
    }

    @Test
    fun `plant beverages stay in the fridge, not the pantry`() {
        val guess = ShelfLife.guess("Unsweetened Vanilla Almond Beverage")
        assertEquals(StorageArea.FRIDGE, guess.storageArea)
        assertEquals(FoodCategory.DAIRY_EGGS, guess.category)
    }

    @Test
    fun `counter fruit does not get a five day fridge life`() {
        val guess = ShelfLife.guess("Bananas")
        assertEquals(FoodCategory.FRUIT, guess.category)
        assertEquals(StorageArea.PANTRY, guess.storageArea)
        assertEquals(7, guess.days)
    }

    @Test
    fun `pantry staples last a year`() {
        val guess = ShelfLife.guess("Jasmine Rice 10lb")
        assertEquals(FoodCategory.GRAINS_NOODLES, guess.category)
        assertEquals(StorageArea.PANTRY, guess.storageArea)
        assertEquals(365, guess.days)
    }

    @Test
    fun `frozen wins the storage area but not the category`() {
        // Frozen shrimp is still seafood — it just lives somewhere else for far longer.
        val shrimp = ShelfLife.guess("Frozen Shrimp 1lb")
        assertEquals(FoodCategory.SEAFOOD, shrimp.category)
        assertEquals(StorageArea.FREEZER, shrimp.storageArea)
        assertEquals(120, shrimp.days)

        val berries = ShelfLife.guess("Frozen Blueberries")
        assertEquals(FoodCategory.FRUIT, berries.category)
        assertEquals(StorageArea.FREEZER, berries.storageArea)
    }

    @Test
    fun `unknown names fall back to a week in the fridge`() {
        val guess = ShelfLife.guess("Zzzz Mystery Box")
        assertEquals(FoodCategory.OTHER, guess.category)
        assertEquals(StorageArea.FRIDGE, guess.storageArea)
        assertEquals(7, guess.days)
    }
}
