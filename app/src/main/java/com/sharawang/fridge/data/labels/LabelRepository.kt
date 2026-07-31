package com.sharawang.fridge.data.labels

import android.content.Context
import android.net.Uri
import com.sharawang.fridge.data.repo.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * The printable zone sheet, and writing it out as a PDF.
 *
 * Export goes through the Storage Access Framework like the backup does, so the app needs no
 * storage permission and the file lands wherever the user points it.
 */
class LabelRepository(
    private val context: Context,
    private val repository: InventoryRepository
) {

    /** The sheet as it stands, updating as the kitchen does. */
    fun sheet(): Flow<LabelSheet> = repository.activeItems().map(LabelSheet::from)

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String = "fridge-labels-$today.pdf"

    /**
     * Rendered from a fresh read rather than from whatever the screen is showing: the export
     * is a snapshot of the kitchen, and the screen may be a second or two behind it.
     *
     * @return how many items made it onto the sheet.
     */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sheet = LabelSheet.from(repository.activeItems().first())
        val strings = labelStrings(context)
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            LabelPdf.render(sheet, strings, stream)
        } ?: throw IOException("Could not open that file for writing")
        sheet.itemCount
    }
}
