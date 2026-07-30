package com.sharawang.fridge.receipt

import android.content.Context
import android.net.Uri
import com.sharawang.fridge.receipt.parser.Parsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** OCR -> store detection -> line parsing, in one call. */
class ReceiptScanner(private val ocr: ReceiptOcr = ReceiptOcr()) {

    suspend fun scan(context: Context, imageUri: Uri): ParsedReceipt =
        withContext(Dispatchers.Default) {
            val rawText = ocr.readRows(context, imageUri)
            if (rawText.isBlank()) return@withContext ParsedReceipt.empty()
            Parsers.forText(rawText).parse(rawText)
        }

    /** Re-parses text already stored on a [com.sharawang.fridge.data.local.Purchase]. */
    fun reparse(rawText: String): ParsedReceipt = Parsers.forText(rawText).parse(rawText)
}
