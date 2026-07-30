package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store

/**
 * H-Mart receipts: long leading UPC/PLU codes, department numbers, occasional Korean
 * product names, weighed produce on a separate "0.86 lb @ 2.99/lb" line.
 */
class HMartParser : LineBasedParser() {

    override val store = Store.HMART

    override fun matches(rawText: String): Boolean {
        val upper = rawText.uppercase()
        return upper.contains("H MART") || upper.contains("HMART") ||
            upper.contains("H-MART") || upper.contains("HANAHREUM")
    }

    override val extraNoise = listOf(
        Regex("""\bDEPT\b|\bDEPARTMENT\b"""),
        Regex("""\bSMART\s?POINT|\bK-?POINT"""),
        Regex("""\bCUSTOMER\s?COPY\b|\bMERCHANT\s?COPY\b"""),
        Regex("""\bQTY\b\s*:?\s*\d+\s*$"""),
        Regex("""\bFOOD\s?STAMP\b""")
    )

    override val extraAbbreviations = mapOf(
        "KIM" to "Kimchi", "KMCHI" to "Kimchi", "GOCHU" to "Gochujang",
        "TFU" to "Tofu", "SOYBN" to "Soybean", "SESME" to "Sesame",
        "MDLE" to "Noodle", "RAMYN" to "Ramyun", "MANDU" to "Mandu",
        "PERILA" to "Perilla", "PRLLA" to "Perilla", "CHIVE" to "Chive",
        "NAPA" to "Napa", "DAIKN" to "Daikon", "ENOKI" to "Enoki",
        "BLGGI" to "Bulgogi", "BULGO" to "Bulgogi", "GALBI" to "Galbi"
    )
}
