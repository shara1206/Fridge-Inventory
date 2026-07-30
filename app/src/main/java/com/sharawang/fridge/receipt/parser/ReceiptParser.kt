package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.receipt.ParsedLine
import com.sharawang.fridge.receipt.ParsedReceipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface ReceiptParser {
    val store: Store

    /** True when this parser recognises the receipt from its OCR text. */
    fun matches(rawText: String): Boolean

    fun parse(rawText: String): ParsedReceipt
}

/**
 * Shared machinery for the "NAME .... 4.99 T" receipt shape that every one of the four
 * stores uses with small variations. Subclasses tweak the noise list, the item-code
 * stripping and the abbreviation table.
 */
abstract class LineBasedParser : ReceiptParser {

    /** Extra noise patterns a specific store needs on top of [COMMON_NOISE]. */
    protected open val extraNoise: List<Regex> = emptyList()

    /** Store-specific abbreviations, merged over [COMMON_ABBREVIATIONS]. */
    protected open val extraAbbreviations: Map<String, String> = emptyMap()

    override fun parse(rawText: String): ParsedReceipt {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<ParsedLine>()
        var pendingQuantity: Double? = null

        for (raw in rawLines) {
            // "2 @ 1.99" / "0.86 lb @ 5.99/lb" applies to the line before or after it.
            val multiplier = multiplierQuantity(raw)
            if (multiplier != null) {
                if (items.isNotEmpty() && looksLikeBareMultiplier(raw)) {
                    val last = items.removeAt(items.lastIndex)
                    items.add(last.copy(quantity = multiplier))
                } else {
                    pendingQuantity = multiplier
                }
                continue
            }

            if (isNoise(raw)) continue

            val price = trailingPriceCents(raw) ?: continue
            val name = cleanName(stripPrice(raw))
            if (name.length < 2) continue
            if (!name.any { it.isLetter() }) continue

            items.add(
                ParsedLine(
                    name = name,
                    quantity = pendingQuantity ?: 1.0,
                    priceCents = price,
                    rawLine = raw,
                    confidence = confidenceFor(name, price)
                )
            )
            pendingQuantity = null
        }

        return ParsedReceipt(
            store = store,
            purchasedOn = findDate(rawText),
            totalCents = findTotalCents(rawLines),
            lines = items,
            rawText = rawText
        )
    }

    // ---- overridable pieces -------------------------------------------------

    protected open fun isNoise(line: String): Boolean {
        val upper = line.uppercase()
        return COMMON_NOISE.any { it.containsMatchIn(upper) } ||
            extraNoise.any { it.containsMatchIn(upper) }
    }

    /** Strips leading UPC / PLU / department codes. */
    protected open fun stripCodes(name: String): String =
        name.replace(LEADING_CODE, "").replace(TRAILING_CODE, "")

    protected open fun cleanName(rawName: String): String {
        var name = stripCodes(rawName.trim())
            .replace(FILLER_DOTS, " ")
            .replace(MULTISPACE, " ")
            .trim()
            .trimEnd('*', '-', '.', ',', '#')
            .trim()

        val abbreviations = COMMON_ABBREVIATIONS + extraAbbreviations
        name = name.split(' ').joinToString(" ") { word ->
            val key = word.uppercase().trim('.', ',', '*')
            abbreviations[key] ?: word
        }
        return titleCase(name)
    }

    protected open fun confidenceFor(name: String, priceCents: Int): Float {
        var score = 0.5f
        if (name.contains(' ')) score += 0.2f            // multi-word names are usually real
        if (name.length in 4..40) score += 0.2f
        if (priceCents in 25..10_000) score += 0.1f      // 0.25 – 100.00
        return score.coerceAtMost(1f)
    }

    // ---- shared helpers ----------------------------------------------------

    protected fun trailingPriceCents(line: String): Int? {
        val match = TRAILING_PRICE.find(line) ?: return null
        return toCents(match.groupValues[1])
    }

    protected fun stripPrice(line: String): String = TRAILING_PRICE.replace(line, "")

    protected fun multiplierQuantity(line: String): Double? {
        WEIGHT_AT.find(line)?.let { return it.groupValues[1].toDoubleOrNull() }
        COUNT_AT.find(line)?.let { return it.groupValues[1].toDoubleOrNull() }
        return null
    }

    private fun looksLikeBareMultiplier(line: String): Boolean =
        line.none { it.isLetter() && it.uppercaseChar() !in "LBSKGEA@X" }

    protected fun findTotalCents(lines: List<String>): Int? {
        val candidates = lines.filter { line ->
            val u = line.uppercase()
            TOTAL_LINE.containsMatchIn(u) && !u.contains("SUBTOTAL") && !u.contains("SUB TOTAL")
        }
        return candidates.mapNotNull { trailingPriceCents(it) }.maxOrNull()
    }

    protected fun findDate(rawText: String): LocalDate? {
        DATE_SLASH.find(rawText)?.let { m ->
            val (a, b, c) = m.destructured
            val year = c.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, a.toInt(), b.toInt()) }.getOrNull()
        }
        DATE_ISO.find(rawText)?.let { m ->
            return runCatching { LocalDate.parse(m.value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        }
        return null
    }

    companion object {
        // 4.99 / 1,234.56, optional trailing tax flag letter, optional leading $
        val TRAILING_PRICE = Regex("""[$]?\s*(-?\d{1,3}(?:,\d{3})*\.\d{2})\s*[A-Za-z]{0,2}\s*$""")
        val LEADING_CODE = Regex("""^[\d#*]{4,}\s*""")
        val TRAILING_CODE = Regex("""\s+[\d]{4,}$""")
        val FILLER_DOTS = Regex("""\.{2,}""")
        val MULTISPACE = Regex("""\s{2,}""")
        val WEIGHT_AT = Regex("""(\d+\.\d{1,3})\s*(?:LB|lb|KG|kg)\s*@""")
        val COUNT_AT = Regex("""^\s*(\d{1,2})\s*(?:@|[xX])\s*[$]?\d""")
        val TOTAL_LINE = Regex("""\bTOTAL\b|\bBALANCE\s+DUE\b""")
        val DATE_SLASH = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b""")
        val DATE_ISO = Regex("""\b\d{4}-\d{2}-\d{2}\b""")

        val COMMON_NOISE: List<Regex> = listOf(
            Regex("""\bSUB\s?TOTAL\b"""), Regex("""\bTOTAL\b"""), Regex("""\bTAX\b"""),
            Regex("""\bCASH\b"""), Regex("""\bCHANGE\b"""), Regex("""\bTENDER"""),
            Regex("""\bVISA\b|\bMASTERCARD\b|\bAMEX\b|\bDEBIT\b|\bCREDIT\b|\bEBT\b"""),
            Regex("""\bAUTH\b|\bAPPROVED\b|\bCHIP\b|\bAID\b|\bREF\s?#"""),
            Regex("""\bTHANK\s?YOU\b|\bTHANKS\b"""),
            Regex("""\bSAVINGS?\b|\bCOUPON\b|\bDISCOUNT\b|\bREWARD"""),
            Regex("""\bMEMBER\b|\bPOINTS?\b|\bLOYALTY\b"""),
            Regex("""\bCASHIER\b|\bSTORE\b|\bREG(ISTER)?\s?#|\bTRANS(ACTION)?\b|\bINVOICE\b"""),
            Regex("""\bRETURN\b|\bREFUND\b|\bPOLICY\b|\bRECEIPT\b|\bSURVEY\b"""),
            Regex("""\bITEMS?\s+(SOLD|COUNT)\b|\b# ?OF ?ITEMS\b"""),
            Regex("""\bBAG\s?FEE\b|\bBOTTLE\s?DEPOSIT\b|\bCRV\b"""),
            Regex("""^\**\d{4}$"""),                          // masked card digits
            Regex("""\b\d{3}[-.]\d{3}[-.]\d{4}\b"""),         // phone numbers
            Regex("""\bWWW\.|\.COM\b|\bHTTP""")
        )

        /** Receipt shorthand seen across all four chains. */
        val COMMON_ABBREVIATIONS: Map<String, String> = mapOf(
            "ORG" to "Organic", "ORGN" to "Organic", "ORGANC" to "Organic",
            "CHKN" to "Chicken", "CHK" to "Chicken", "BF" to "Beef", "PRK" to "Pork",
            "GRND" to "Ground", "GRD" to "Ground", "BNLS" to "Boneless", "BNLSS" to "Boneless",
            "SKNLS" to "Skinless", "THGH" to "Thigh", "BRST" to "Breast",
            "SPNCH" to "Spinach", "SPNH" to "Spinach", "LTUCE" to "Lettuce", "LTC" to "Lettuce",
            "TOM" to "Tomato", "TMTO" to "Tomato", "CUC" to "Cucumber", "BRCLI" to "Broccoli",
            "BROC" to "Broccoli", "CRRT" to "Carrot", "MSHRM" to "Mushroom", "MUSH" to "Mushroom",
            "SCLLN" to "Scallion", "GRN" to "Green", "ONN" to "Onion", "GARLC" to "Garlic",
            "PTTO" to "Potato", "SWT" to "Sweet", "AVCDO" to "Avocado", "BNNA" to "Banana",
            "STRWBRY" to "Strawberry", "BLBRY" to "Blueberry", "RSPBRY" to "Raspberry",
            "MLK" to "Milk", "WHL" to "Whole", "YOG" to "Yogurt", "YGRT" to "Yogurt",
            "CHZ" to "Cheese", "CHS" to "Cheese", "BTTR" to "Butter", "CRM" to "Cream",
            "EGGS" to "Eggs", "LRG" to "Large", "XL" to "Extra Large",
            "SHRMP" to "Shrimp", "SLMN" to "Salmon", "TLPIA" to "Tilapia",
            "FRZ" to "Frozen", "FRZN" to "Frozen", "DMPLNG" to "Dumpling",
            "NDL" to "Noodle", "NDLS" to "Noodles", "RC" to "Rice",
            "SCE" to "Sauce", "SAU" to "Sauce", "VNGR" to "Vinegar", "OL" to "Oil",
            "SNCK" to "Snack", "BVRG" to "Beverage", "JC" to "Juice", "WTR" to "Water",
            "LB" to "lb", "OZ" to "oz", "PK" to "Pack", "PKG" to "Package", "CT" to "Count",
            "BNCH" to "Bunch", "BAG" to "Bag", "BX" to "Box"
        )

        fun toCents(text: String): Int? {
            val normalized = text.replace(",", "")
            val value = normalized.toDoubleOrNull() ?: return null
            return Math.round(value * 100).toInt()
        }

        fun titleCase(text: String): String = text.split(' ').joinToString(" ") { word ->
            when {
                word.isEmpty() -> word
                word.length <= 2 && word.all { it.isUpperCase() } -> word  // T&T, XL
                word.any { it.isLowerCase() } -> word                      // already cased
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }
}
