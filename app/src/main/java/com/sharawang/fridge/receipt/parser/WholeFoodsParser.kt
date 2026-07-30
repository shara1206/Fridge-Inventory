package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store

/**
 * Whole Foods receipts: "365" house-brand prefixes, weighed items priced per pound,
 * Prime member savings lines that must not be mistaken for products.
 */
class WholeFoodsParser : LineBasedParser() {

    override val store = Store.WHOLE_FOODS

    override fun matches(rawText: String): Boolean {
        val upper = rawText.uppercase()
        return upper.contains("WHOLE FOODS") || upper.contains("WFM") ||
            (upper.contains("AMAZON") && upper.contains("MARKET"))
    }

    override val extraNoise = listOf(
        Regex("""\bPRIME\b|\bAMZN\b|\bAMAZON\b"""),
        Regex("""\bSNAP\b|\bWIC\b"""),
        Regex("""\bYOU\s?SAVED\b|\bREG\s?PRICE\b|\bSALE\s?PRICE\b"""),
        Regex("""\bNET\s?WT\b|\bPRICE\s?PER\b|\bPER\s?LB\b""")
    )

    override val extraAbbreviations = mapOf(
        "365" to "365", "WF" to "Whole Foods", "GG" to "Grass Fed",
        "PSTR" to "Pasture", "RSPNSBL" to "Responsibly", "SSTNBL" to "Sustainable",
        "AIRCHL" to "Air Chilled", "NTRL" to "Natural", "VEG" to "Vegetable"
    )
}
