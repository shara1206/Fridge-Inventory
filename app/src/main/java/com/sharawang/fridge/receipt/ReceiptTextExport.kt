package com.sharawang.fridge.receipt

import com.sharawang.fridge.data.local.Purchase
import com.sharawang.fridge.data.local.Store
import java.time.LocalDate

/**
 * Plain-text dump of what OCR read off receipts, for sharing when a parse goes wrong.
 *
 * Parser fixes need the text exactly as ML Kit produced it — its misreadings and line breaks
 * are the bug report — and a photo cannot reproduce that off the device. Each receipt gets a
 * header saying which parser picked it up, so a misdetected store is visible at a glance.
 *
 * Plain Kotlin on purpose, like the parsers, so it tests on the JVM.
 */
object ReceiptTextExport {

    fun format(purchases: List<Purchase>): String =
        purchases.joinToString(separator = "\n\n") { purchase ->
            receipt(
                rawText = purchase.rawText,
                store = purchase.store,
                purchasedOn = purchase.purchasedOn,
                id = purchase.id
            )
        }

    /** One receipt. [id] is the saved row, when there is one, so it can be found again. */
    fun receipt(rawText: String, store: Store, purchasedOn: LocalDate?, id: Long? = null): String {
        val header = listOfNotNull(
            id?.let { "#$it" },
            "parser: ${store.label}",
            purchasedOn?.toString() ?: "date: unknown"
        ).joinToString(" · ")
        val body = rawText.trimEnd().ifEmpty { "(OCR found no text)" }
        return "=== $header ===\n$body\n=== end ==="
    }
}
