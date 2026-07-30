package com.sharawang.fridge.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR. The model ships inside the APK (see the ML Kit meta-data in the
 * manifest), so scanning works with no network and no receipt image ever leaving
 * the phone.
 */
class ReceiptOcr {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Returns the receipt text, one physical row per line. */
    suspend fun readRows(context: Context, imageUri: Uri): String {
        val image = InputImage.fromFilePath(context, imageUri)
        val visionText = suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val fragments = visionText.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                TextLayout.Fragment(
                    text = line.text,
                    left = box.left,
                    top = box.top,
                    bottom = box.bottom
                )
            }

        return TextLayout.toRows(fragments).joinToString("\n")
    }

    fun close() = recognizer.close()
}
