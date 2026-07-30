package com.sharawang.fridge.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One scanned or manually logged shopping trip. */
@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val store: Store,
    val purchasedOn: LocalDate,
    val totalCents: Int? = null,
    /** Full OCR dump, kept so parsers can be improved and re-run later. */
    val rawText: String = "",
    /** Local file path of the receipt photo, if kept. */
    val imagePath: String? = null
)

/**
 * A single line of a receipt as the parser saw it. Kept even after the user edits the
 * resulting [FoodItem], so parser bugs are debuggable against real receipts.
 */
@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("purchaseId")]
)
data class PurchaseLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val rawLine: String,
    val parsedName: String,
    val quantity: Double = 1.0,
    val priceCents: Int? = null,
    /** False when the user rejected the line during review. */
    val accepted: Boolean = true,
    val foodItemId: Long? = null
)
