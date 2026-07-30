package com.sharawang.fridge.receipt

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Receipt photos land in the app cache and are exposed to the camera app through the
 * FileProvider declared in the manifest. Nothing is written to shared storage.
 */
object ReceiptImageStore {

    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Deletes receipt photos older than [days]. Called on app start: without it the cache
     * grows by a photo per shopping trip forever, and the raw OCR text stored on the
     * Purchase row is what parser debugging actually needs — not the image.
     *
     * Returns the number of files removed, which makes it testable.
     */
    fun pruneOlderThan(context: Context, days: Long = 30): Int {
        val cutoff = System.currentTimeMillis() - days * MILLIS_PER_DAY
        val stale = directory(context).listFiles()?.filter { it.lastModified() < cutoff }.orEmpty()
        return stale.count { it.delete() }
    }

    private fun directory(context: Context): File = File(context.cacheDir, "receipts")

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
}
