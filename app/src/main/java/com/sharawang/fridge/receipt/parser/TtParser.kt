package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store

/**
 * T&T Supermarket receipts: bilingual item lines (Chinese name next to the English one),
 * Canadian tax codes after the price, item codes at the front.
 *
 * The Chinese half of the name is kept — it is often the more recognisable half.
 */
class TtParser : LineBasedParser() {

    override val store = Store.TT

    override fun matches(rawText: String): Boolean {
        val upper = rawText.uppercase()
        return upper.contains("T&T") || upper.contains("T & T") ||
            upper.contains("TNT SUPERMARKET") || rawText.contains("大統華")
    }

    override val extraNoise = listOf(
        Regex("""\bGST\b|\bPST\b|\bHST\b|\bQST\b"""),
        Regex("""\bAIR\s?MILES\b|\bT&T\s?REWARD"""),
        Regex("""\bCAD\b|\bDEPOSIT\b|\bENVIRO\b|\bECO\s?FEE\b"""),
        Regex("""\bDEBIT\s?CARD\b|\bINTERAC\b""")
    )

    override val extraAbbreviations = mapOf(
        "CHOY" to "Choy", "BOKCHY" to "Bok Choy", "GAILAN" to "Gai Lan",
        "YUCHOI" to "Yu Choy", "TOFU" to "Tofu", "DOUFU" to "Tofu",
        "WNTN" to "Wonton", "DMPL" to "Dumpling", "BBQ" to "BBQ",
        "SHTKE" to "Shiitake", "SHIITK" to "Shiitake", "LTS" to "Lotus",
        "PORKBLY" to "Pork Belly", "FSH" to "Fish", "STMD" to "Steamed"
    )

    /** Item codes at T&T can be 5–13 digits and sometimes trail the Chinese name. */
    override fun stripCodes(name: String): String =
        super.stripCodes(name).replace(Regex("""\s+\d{5,}\s*"""), " ").trim()
}
