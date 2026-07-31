package com.sharawang.fridge.data.backup

import android.content.Context
import android.net.Uri
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.isFinished
import com.sharawang.fridge.data.repo.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** How an import should treat what is already in the database. */
enum class ImportMode {
    /** Fold the file into the current kitchen, merging repeats by name and area. */
    MERGE,

    /** Wipe first. For moving to a new phone, where "merge" would double everything. */
    REPLACE
}

/**
 * Export and import of the whole inventory as a file the user owns.
 *
 * Writes through the Storage Access Framework, so the app needs no storage permission and
 * the user picks where the file lands.
 */
class BackupRepository(
    private val context: Context,
    private val repository: InventoryRepository
) {

    /** Suggested filename; the user can rename it in the system picker. */
    fun suggestedFileName(today: LocalDate = LocalDate.now()): String = "fridge-inventory-$today.json"

    /** @return how many rows were written. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val items = repository.allItems()
        val text = InventoryBackup.encode(items)
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray())
        } ?: throw BackupFormatException("Could not open that file for writing")
        items.size
    }

    /** @return how many rows were read out of the file. */
    suspend fun importFrom(uri: Uri, mode: ImportMode): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw BackupFormatException("Could not open that file")

        val items = InventoryBackup.decode(text)
        when (mode) {
            ImportMode.REPLACE -> repository.replaceAll(items)
            ImportMode.MERGE -> merge(items)
        }
        items.size
    }

    /**
     * Finished rows are history and simply appended — merging them would rewrite the past.
     * Live rows go through the same merge path as a repeat purchase, so importing a backup
     * onto a kitchen that already has spinach in it reads as "2 spinach", not two lines.
     */
    private suspend fun merge(items: List<FoodItem>) {
        val (history, live) = items.partition { it.isFinished }
        if (history.isNotEmpty()) repository.insertAll(history)
        live.forEach { repository.addOrMerge(it) }
    }
}
