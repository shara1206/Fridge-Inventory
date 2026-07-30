package com.sharawang.fridge.data

import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.StorageArea

/**
 * Best-guess shelf life so the user rarely has to type an expiry date.
 *
 * Keyword matching is deliberately dumb and dependency-free: the longest matching keyword
 * wins, so "chicken thigh" beats "chicken". Values are conservative fridge-life estimates
 * in days, from USDA / FoodKeeper style guidance. The user can always override.
 */
object ShelfLife {

    data class Guess(
        val category: FoodCategory,
        val storageArea: StorageArea,
        val days: Int
    )

    private data class Rule(
        val keywords: List<String>,
        val category: FoodCategory,
        val area: StorageArea,
        val days: Int
    )

    private val rules = listOf(
        // --- Vegetables -----------------------------------------------------
        // Leafy and delicate: the things that actually rot before you notice.
        Rule(listOf("spinach", "arugula", "spring mix", "lettuce", "romaine", "bok choy",
            "choy", "napa", "cilantro", "basil", "green onion", "scallion", "chive", "herb",
            "mushroom", "enoki", "shiitake", "bean sprout", "gai lan", "watercress"),
            FoodCategory.VEGETABLES, StorageArea.FRIDGE, 5),
        // Sturdy: survives a week or two in the crisper.
        Rule(listOf("carrot", "celery", "cabbage", "broccoli", "cauliflower", "pepper",
            "cucumber", "zucchini", "eggplant", "tomato", "daikon", "lotus", "ginger",
            "garlic", "kabocha", "leek", "asparagus", "green bean", "okra", "corn"),
            FoodCategory.VEGETABLES, StorageArea.FRIDGE, 12),
        // Keeps at room temperature.
        Rule(listOf("potato", "sweet potato", "onion", "squash", "pumpkin", "taro", "yam"),
            FoodCategory.VEGETABLES, StorageArea.PANTRY, 21),

        // --- Fruit ----------------------------------------------------------
        Rule(listOf("berry", "strawberr", "raspberr", "blueberr", "blackberr", "cherr",
            "fig", "grape"),
            FoodCategory.FRUIT, StorageArea.FRIDGE, 5),
        Rule(listOf("apple", "orange", "lemon", "lime", "pear", "melon", "pineapple",
            "grapefruit", "mandarin", "persimmon", "pomelo"),
            FoodCategory.FRUIT, StorageArea.FRIDGE, 14),
        // Ripens on the counter.
        Rule(listOf("banana", "mango", "avocado", "peach", "nectarine", "plum", "kiwi",
            "papaya", "lychee"),
            FoodCategory.FRUIT, StorageArea.PANTRY, 7),

        // --- Meat -----------------------------------------------------------
        Rule(listOf("chicken", "pork", "beef", "lamb", "turkey", "duck", "ground", "steak",
            "belly", "thigh", "breast", "rib", "bulgogi", "galbi", "bacon", "sausage",
            "mince", "brisket", "shank", "tenderloin"),
            FoodCategory.MEAT, StorageArea.FRIDGE, 3),

        // --- Seafood --------------------------------------------------------
        // The shortest fridge life of anything in the app.
        Rule(listOf("salmon", "tuna", "cod", "shrimp", "prawn", "squid", "octopus", "fish",
            "scallop", "oyster", "clam", "mussel", "crab", "eel", "tilapia", "snapper",
            "mackerel", "roe", "sashimi"),
            FoodCategory.SEAFOOD, StorageArea.FRIDGE, 2),

        // --- Tofu and soy ---------------------------------------------------
        Rule(listOf("tofu", "bean curd", "doufu", "tempeh", "natto", "soy sheet",
            "fried tofu", "aburaage", "yuba"),
            FoodCategory.TOFU_SOY, StorageArea.FRIDGE, 7),

        // --- Dairy and eggs -------------------------------------------------
        Rule(listOf("milk", "cream", "half and half", "oat milk", "soy milk", "almond milk",
            "almond beverage", "soy beverage", "oat beverage", "coconut beverage"),
            FoodCategory.DAIRY_EGGS, StorageArea.FRIDGE, 10),
        Rule(listOf("yogurt", "yoghurt", "kefir", "cottage", "sour cream"),
            FoodCategory.DAIRY_EGGS, StorageArea.FRIDGE, 21),
        Rule(listOf("cheese", "butter", "ghee"),
            FoodCategory.DAIRY_EGGS, StorageArea.FRIDGE, 30),
        Rule(listOf("egg"), FoodCategory.DAIRY_EGGS, StorageArea.FRIDGE, 28),

        // --- Frozen staples -------------------------------------------------
        Rule(listOf("frozen", "dumpling", "gyoza", "mandu", "wonton", "bao", "mantou",
            "spring roll", "ice cream", "popsicle", "edamame", "rice cake", "tteok",
            "frozen noodle", "pierogi"),
            FoodCategory.FROZEN_STAPLES, StorageArea.FREEZER, 120),

        // --- Grains and noodles ---------------------------------------------
        Rule(listOf("rice", "noodle", "pasta", "flour", "cereal", "oat", "quinoa", "lentil",
            "dried bean", "vermicelli", "udon", "ramen", "couscous", "barley"),
            FoodCategory.GRAINS_NOODLES, StorageArea.PANTRY, 365),

        // --- Seasoning ------------------------------------------------------
        Rule(listOf("oil", "sauce", "soy", "vinegar", "salt", "sugar", "spice", "peppercorn",
            "gochujang", "doubanjiang", "sesame", "miso", "mirin", "starch", "bouillon",
            "curry", "paste", "honey", "syrup"),
            FoodCategory.SEASONING, StorageArea.PANTRY, 365),

        // --- Snacks ---------------------------------------------------------
        Rule(listOf("snack", "chip", "cracker", "cookie", "biscuit", "nut", "almond",
            "cashew", "peanut", "seaweed", "nori", "jerky", "candy", "chocolate"),
            FoodCategory.SNACKS, StorageArea.PANTRY, 180),

        // --- Drinks ---------------------------------------------------------
        Rule(listOf("juice", "soda", "sparkling", "kombucha", "coffee", "tea", "beer",
            "wine", "seltzer", "water"),
            FoodCategory.BEVERAGE, StorageArea.PANTRY, 180),

        // --- Ready to eat ---------------------------------------------------
        Rule(listOf("bread", "bun", "bagel", "tortilla", "cake", "pastry", "croissant",
            "muffin", "donut"),
            FoodCategory.PREPARED, StorageArea.PANTRY, 5),
        Rule(listOf("salad kit", "sandwich", "sushi", "poke", "prepared", "hummus", "dip",
            "roast chicken", "rotisserie", "deli", "banchan"),
            FoodCategory.PREPARED, StorageArea.FRIDGE, 3),
        // Fermented things keep for months, so they must not inherit the deli rule.
        Rule(listOf("kimchi", "sauerkraut", "pickle", "jangajji"),
            FoodCategory.PREPARED, StorageArea.FRIDGE, 90)
    )

    private val fallback = Guess(FoodCategory.OTHER, StorageArea.FRIDGE, 7)

    /**
     * "Frozen" is orthogonal to category — frozen shrimp is still seafood, it just lives in
     * the freezer and keeps for months. So the keyword rules decide the category and these
     * markers override where it lives and how long it lasts.
     */
    private val frozenMarkers = listOf("frozen", "frzn", "ice cream", "popsicle")

    private const val FREEZER_DAYS = 120

    /** Longest keyword match wins; falls back to a week in the fridge. */
    fun guess(rawName: String): Guess {
        val name = rawName.lowercase()
        var best: Pair<Rule, Int>? = null
        for (rule in rules) {
            for (keyword in rule.keywords) {
                if (name.contains(keyword) && (best == null || keyword.length > best!!.second)) {
                    best = rule to keyword.length
                }
            }
        }
        val rule = best?.first
        val guess = if (rule == null) fallback else Guess(rule.category, rule.area, rule.days)
        return applyFrozenOverride(name, guess)
    }

    private fun applyFrozenOverride(lowercaseName: String, guess: Guess): Guess {
        if (guess.storageArea == StorageArea.FREEZER) return guess
        if (frozenMarkers.none { lowercaseName.contains(it) }) return guess
        return guess.copy(
            storageArea = StorageArea.FREEZER,
            days = maxOf(guess.days, FREEZER_DAYS)
        )
    }
}
