// Port of data/ShelfLife.kt. The rule table is generated from the Kotlin source so the two
// apps guess the same category and storage area for the same name.
//
// Longest matching keyword wins, so "chicken thigh" beats "chicken". Days are conservative
// fridge-life estimates; the app no longer pre-fills expiry from them, but they are kept so
// the table stays identical to Android's.

const RULES = [
  [["spinach", "arugula", "spring mix", "lettuce", "romaine", "bok choy", "choy", "napa", "cilantro", "basil", "green onion", "scallion", "chive", "herb", "mushroom", "enoki", "shiitake", "bean sprout", "gai lan", "watercress"], 'VEGETABLES', 'FRIDGE', 5],
  [["carrot", "celery", "cabbage", "broccoli", "cauliflower", "pepper", "cucumber", "zucchini", "eggplant", "tomato", "daikon", "lotus", "ginger", "garlic", "kabocha", "leek", "asparagus", "green bean", "okra", "corn"], 'VEGETABLES', 'FRIDGE', 12],
  [["potato", "sweet potato", "onion", "squash", "pumpkin", "taro", "yam"], 'VEGETABLES', 'PANTRY', 21],
  [["berry", "strawberr", "raspberr", "blueberr", "blackberr", "cherr", "fig", "grape"], 'FRUIT', 'FRIDGE', 5],
  [["apple", "orange", "lemon", "lime", "pear", "melon", "pineapple", "grapefruit", "mandarin", "persimmon", "pomelo"], 'FRUIT', 'FRIDGE', 14],
  [["banana", "mango", "avocado", "peach", "nectarine", "plum", "kiwi", "papaya", "lychee"], 'FRUIT', 'PANTRY', 7],
  [["chicken", "pork", "beef", "lamb", "turkey", "duck", "ground", "steak", "belly", "thigh", "breast", "rib", "bulgogi", "galbi", "bacon", "sausage", "mince", "brisket", "shank", "tenderloin"], 'MEAT', 'FRIDGE', 3],
  [["salmon", "tuna", "cod", "shrimp", "prawn", "squid", "octopus", "fish", "scallop", "oyster", "clam", "mussel", "crab", "eel", "tilapia", "snapper", "mackerel", "roe", "sashimi"], 'SEAFOOD', 'FRIDGE', 2],
  [["tofu", "bean curd", "doufu", "tempeh", "natto", "soy sheet", "fried tofu", "aburaage", "yuba"], 'TOFU_SOY', 'FRIDGE', 7],
  [["milk", "cream", "half and half", "oat milk", "soy milk", "almond milk", "almond beverage", "soy beverage", "oat beverage", "coconut beverage"], 'DAIRY_EGGS', 'FRIDGE', 10],
  [["yogurt", "yoghurt", "kefir", "cottage", "sour cream"], 'DAIRY_EGGS', 'FRIDGE', 21],
  [["cheese", "butter", "ghee"], 'DAIRY_EGGS', 'FRIDGE', 30],
  [["egg"], 'DAIRY_EGGS', 'FRIDGE', 28],
  [["frozen", "dumpling", "gyoza", "mandu", "wonton", "bao", "mantou", "spring roll", "ice cream", "popsicle", "edamame", "rice cake", "tteok", "frozen noodle", "pierogi"], 'FROZEN_STAPLES', 'FREEZER', 120],
  [["rice", "noodle", "pasta", "flour", "cereal", "oat", "quinoa", "lentil", "dried bean", "vermicelli", "udon", "ramen", "couscous", "barley"], 'GRAINS_NOODLES', 'PANTRY', 365],
  [["oil", "sauce", "soy", "vinegar", "salt", "sugar", "spice", "peppercorn", "gochujang", "doubanjiang", "sesame", "miso", "mirin", "starch", "bouillon", "curry", "paste", "honey", "syrup"], 'SEASONING', 'PANTRY', 365],
  [["snack", "chip", "cracker", "cookie", "biscuit", "nut", "almond", "cashew", "peanut", "seaweed", "nori", "jerky", "candy", "chocolate"], 'SNACKS', 'PANTRY', 180],
  [["juice", "soda", "sparkling", "kombucha", "coffee", "tea", "beer", "wine", "seltzer", "water"], 'BEVERAGE', 'PANTRY', 180],
  [["bread", "bun", "bagel", "tortilla", "cake", "pastry", "croissant", "muffin", "donut"], 'PREPARED', 'PANTRY', 5],
  [["salad kit", "sandwich", "sushi", "poke", "prepared", "hummus", "dip", "roast chicken", "rotisserie", "deli", "banchan"], 'PREPARED', 'FRIDGE', 3],
  [["kimchi", "sauerkraut", "pickle", "jangajji"], 'PREPARED', 'FRIDGE', 90],
];

const FALLBACK = { category: 'OTHER', storageArea: 'FRIDGE', days: 7 };

/**
 * "Frozen" is orthogonal to category: frozen shrimp is still seafood, it just lives in the
 * freezer. The keyword rules decide the category and these markers override where it lives.
 */
const FROZEN_MARKERS = ['frozen', 'frzn', 'ice cream', 'popsicle'];
const FREEZER_DAYS = 120;

export function guess(rawName) {
  const name = rawName.toLowerCase();
  let best = null;
  let bestLength = 0;
  for (const rule of RULES) {
    for (const keyword of rule[0]) {
      if (name.includes(keyword) && (best === null || keyword.length > bestLength)) {
        best = rule;
        bestLength = keyword.length;
      }
    }
  }
  const result = best
    ? { category: best[1], storageArea: best[2], days: best[3] }
    : { ...FALLBACK };
  if (result.storageArea !== 'FREEZER' && FROZEN_MARKERS.some((m) => name.includes(m))) {
    result.storageArea = 'FREEZER';
    result.days = Math.max(result.days, FREEZER_DAYS);
  }
  return result;
}
