package com.sharawang.fridge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One thing in the kitchen. A single row is created per receipt line the user accepts,
 * or per manual entry.
 */
@Entity(
    tableName = "food_items",
    indices = [Index("expiresOn"), Index("finishedOn"), Index("purchaseId")]
)
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: FoodCategory = FoodCategory.OTHER,
    val storageArea: StorageArea = StorageArea.FRIDGE,
    val quantity: Double = 1.0,
    /**
     * Vestigial. Quantity is a bare count in the UI now — "2 bunch" was never worth the
     * field it cost, since the name already says what the thing is. The column stays so
     * that units written by earlier versions survive in the database and in backups, and
     * so that bringing the idea back needs no migration.
     */
    val unit: String = "ea",
    val purchasedOn: LocalDate = LocalDate.now(),
    /** Null means "no expiry tracked". */
    val expiresOn: LocalDate? = null,
    val store: Store = Store.OTHER,
    /** Stored in cents to avoid float money bugs. */
    @ColumnInfo(name = "priceCents") val priceCents: Int? = null,
    val notes: String = "",
    /** Set when eaten / thrown out. Null = still in the kitchen. */
    val finishedOn: LocalDate? = null,
    /** Null while the item is still here; set alongside [finishedOn]. */
    val finishedReason: FinishReason? = null,
    /** Receipt this row came from, if it was scanned. */
    val purchaseId: Long? = null
)

/*
 * Derived values live outside the entity: anything with a custom getter inside an
 * @Entity invites Room column-mapping surprises.
 */

val FoodItem.isFinished: Boolean get() = finishedOn != null

/** Negative = already expired. Null = expiry not tracked. */
fun FoodItem.daysLeft(today: LocalDate = LocalDate.now()): Long? =
    expiresOn?.let { ChronoUnit.DAYS.between(today, it) }
