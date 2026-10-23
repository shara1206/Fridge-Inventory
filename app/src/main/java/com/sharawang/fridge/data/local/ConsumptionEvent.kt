package com.sharawang.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One thing that left the kitchen, on the day it left.
 *
 * The item row cannot carry this on its own. `finishedOn` / `finishedReason` describe a row
 * that is gone, so eating two of three eggs — the ordinary case — left no trace at all: the
 * count dropped and nothing was recorded. This table is the record, one line per − tap and
 * per ✓, so "what did we get through this month" is answerable.
 *
 * Name, category, area and store are *snapshots*, not joins. History is a statement about
 * what happened, and renaming an item, re-filing it, or clearing it out of the database
 * later must not rewrite it. [itemId] is kept only so undo can find the row to put back,
 * and is allowed to dangle.
 */
@Entity(
    tableName = "consumption_events",
    indices = [Index("occurredOn"), Index("itemId")]
)
data class ConsumptionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The row this came off, if it still exists. Deliberately not a foreign key. */
    val itemId: Long? = null,
    val name: String,
    val category: FoodCategory = FoodCategory.OTHER,
    val storageArea: StorageArea = StorageArea.FRIDGE,
    val store: Store = Store.OTHER,
    /** How much left the kitchen — not how much was left over. */
    val quantity: Double = 1.0,
    /**
     * Null only for rows carried over from before this table existed, where the app knew
     * something was finished but not whether it was eaten. Waste percentages skip those
     * rather than guessing.
     */
    val reason: FinishReason? = null,
    val occurredOn: LocalDate = LocalDate.now(),
    /**
     * Wall-clock milliseconds, used only to order several events on the same day. Kept
     * separate from [occurredOn] so a date the user picks by hand cannot disturb ordering.
     */
    val recordedAt: Long = 0L,
    /**
     * This event's share of what the row cost, in cents. Prorated: half a $4 pack of pork
     * is 200. Null when the row had no price, which is not the same as free.
     */
    val valueCents: Int? = null
)
