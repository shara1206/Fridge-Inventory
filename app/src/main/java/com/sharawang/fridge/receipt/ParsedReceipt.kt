package com.sharawang.fridge.receipt

import com.sharawang.fridge.data.local.Store
import java.time.LocalDate

/** One candidate grocery line lifted out of a receipt. */
data class ParsedLine(
    val name: String,
    val quantity: Double = 1.0,
    val priceCents: Int? = null,
    val rawLine: String = "",
    /** 0..1 — how sure the parser is. Low confidence lines are flagged in review. */
    val confidence: Float = 0.5f
)

data class ParsedReceipt(
    val store: Store,
    val purchasedOn: LocalDate?,
    val totalCents: Int?,
    val lines: List<ParsedLine>,
    val rawText: String
) {
    companion object {
        fun empty(rawText: String = "") =
            ParsedReceipt(Store.OTHER, null, null, emptyList(), rawText)
    }
}
