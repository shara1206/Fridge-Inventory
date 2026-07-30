package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store

/**
 * Trader Joe's receipts are the tidiest of the four: plain product name, price on the
 * same line, quantities as "2 @ 3.99", an item count in the footer.
 */
class TraderJoesParser : LineBasedParser() {

    override val store = Store.TRADER_JOES

    override fun matches(rawText: String): Boolean {
        val upper = rawText.uppercase()
        return upper.contains("TRADER JOE") || upper.contains("TRADER JOE'S")
    }

    override val extraNoise = listOf(
        Regex("""\bITEMS?\b\s*\d*\s*$"""),
        Regex("""\bOPEN\s?DAILY\b|\bBALANCE\b"""),
        Regex("""\bTJ'?S\b\s*$""")
    )

    override val extraAbbreviations = mapOf(
        "EVOO" to "Olive Oil", "MED" to "Medium", "SM" to "Small",
        "SLD" to "Salad", "SNP" to "Snap", "RSTD" to "Roasted",
        "UNSWT" to "Unsweetened", "GF" to "Gluten Free"
    )
}
