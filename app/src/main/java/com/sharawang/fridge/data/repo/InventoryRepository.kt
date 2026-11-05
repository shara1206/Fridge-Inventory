package com.sharawang.fridge.data.repo

import com.sharawang.fridge.data.ShelfLife
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.ConsumptionEventDao
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.FoodItemDao
import com.sharawang.fridge.data.local.Purchase
import com.sharawang.fridge.data.local.PurchaseDao
import com.sharawang.fridge.data.local.PurchaseLine
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.data.local.isFinished
import com.sharawang.fridge.receipt.ParsedLine
import com.sharawang.fridge.receipt.ParsedReceipt
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A receipt line the user has approved, after any edits made on the review screen.
 *
 * Kept separate from [ParsedLine] so the parser output stays a faithful record of what the
 * OCR said, and so the repository never has to import anything from the UI layer.
 */
data class ReceiptEntry(
    val line: ParsedLine,
    val name: String,
    val storageArea: StorageArea? = null
)

/** Result of a save, so callers can tell the user which of the three things happened. */
sealed interface MergeOutcome {
    val id: Long

    data class Inserted(override val id: Long) : MergeOutcome
    data class Merged(override val id: Long) : MergeOutcome
    data class Updated(override val id: Long) : MergeOutcome
}

/**
 * Single entry point to the local database. No network, ever.
 *
 * Every public method that writes more than once runs inside [transactions], so a crash or
 * a constraint failure part-way through leaves the database as it was rather than half
 * updated. That matters most for [replaceAll], which deletes everything before inserting.
 */
class InventoryRepository(
    private val foodItemDao: FoodItemDao,
    private val purchaseDao: PurchaseDao,
    private val consumptionEventDao: ConsumptionEventDao,
    private val transactions: TransactionRunner
) {

    constructor(database: AppDatabase) : this(
        database.foodItemDao(),
        database.purchaseDao(),
        database.consumptionEventDao(),
        RoomTransactionRunner(database)
    )

    /** For callers outside the repository that need several of its writes to land together. */
    suspend fun <R> inTransaction(block: suspend () -> R): R = transactions.run(block)

    fun activeItems(): Flow<List<FoodItem>> = foodItemDao.observeActive()

    fun itemsIn(area: StorageArea): Flow<List<FoodItem>> = foodItemDao.observeByArea(area)

    fun search(query: String): Flow<List<FoodItem>> = foodItemDao.search(query)

    /**
     * What left the kitchen, newest first — every − tap and every ✓, not just the taps that
     * happened to empty a row.
     */
    fun history(): Flow<List<ConsumptionEvent>> = consumptionEventDao.observeRecent()

    /** Waste report over the last [days] days, measured in what was consumed. */
    fun wasteReport(days: Long = 30): Flow<WasteReport> =
        consumptionEventDao.observeSince(LocalDate.now().minusDays(days))
            .map(WasteReport::from)

    fun recentPurchases(): Flow<List<Purchase>> = purchaseDao.observeRecent()

    /** Every scanned receipt, newest scan first. The OCR text export reads through this. */
    suspend fun allPurchases(): List<Purchase> = purchaseDao.getAll()

    suspend fun item(id: Long): FoodItem? = foodItemDao.getById(id)

    /** Everything that expires on or before [date] and has not been used up. */
    suspend fun expiringBy(date: LocalDate): List<FoodItem> = foodItemDao.expiringBy(date)

    /** Manual save. New items go through the merge path; edits update in place. */
    suspend fun save(item: FoodItem): MergeOutcome = if (item.id == 0L) {
        addOrMerge(item)
    } else {
        foodItemDao.update(item)
        MergeOutcome.Updated(item.id)
    }

    suspend fun delete(item: FoodItem) = foodItemDao.delete(item)

    /**
     * Clears the row out, logging whatever was still on it as eaten or binned.
     *
     * @return the id of the logged event, for undo. Null if there was nothing to finish —
     * a row already gone, or a second ✓ on the same one, which must not log twice and
     * quietly double what the waste report thinks left the kitchen.
     */
    suspend fun markFinished(
        id: Long,
        reason: FinishReason,
        on: LocalDate = LocalDate.now()
    ): Long? = transactions.run {
        val item = foodItemDao.getById(id) ?: return@run null
        if (item.isFinished) return@run null
        val eventId = record(item, item.quantity, reason, on)
        foodItemDao.markFinished(id, on, reason)
        eventId
    }

    /**
     * Undoes one logged event: the amount goes back on the row and the event is deleted
     * rather than cancelled out. A log the user has to read as a running balance of
     * mistakes is worse than no log.
     *
     * The quantity comes off the event, not off the item, because finishing can happen
     * after a partial use and putting back the original amount would quietly invent food.
     */
    suspend fun undoConsumption(eventId: Long): Unit = transactions.run {
        val event = consumptionEventDao.getById(eventId) ?: return@run
        val item = event.itemId?.let { foodItemDao.getById(it) }
        if (item != null) {
            if (item.isFinished) {
                foodItemDao.restore(item.id, event.quantity)
            } else {
                foodItemDao.updateQuantity(item.id, item.quantity + event.quantity)
            }
        }
        consumptionEventDao.deleteById(eventId)
    }

    /**
     * You bought another one. Not a correction — a purchase — so the purchase date moves to
     * today, exactly as it would if the same item came in on a receipt.
     *
     * The expiry date is left alone on purpose: the older stock is still the stock that
     * needs eating first, and the new unit does not make it keep any longer.
     */
    suspend fun addOne(item: FoodItem, on: LocalDate = LocalDate.now()) {
        foodItemDao.update(
            item.copy(
                quantity = item.quantity + 1,
                purchasedOn = maxOf(item.purchasedOn, on)
            )
        )
    }

    /** Full table, finished rows included. Backup export reads through this. */
    suspend fun allItems(): List<FoodItem> = foodItemDao.getAll()

    /**
     * Restores a backup wholesale. Only reached behind an explicit confirmation.
     *
     * The log is cleared alongside the items: leaving it would attach the old kitchen's
     * history to the new one, and its itemIds would point at rows that no longer exist.
     */
    suspend fun replaceAll(
        items: List<FoodItem>,
        events: List<ConsumptionEvent> = emptyList()
    ): Unit = transactions.run {
        foodItemDao.deleteAll()
        consumptionEventDao.deleteAll()
        foodItemDao.insertAll(items)
        consumptionEventDao.insertAll(events)
    }

    suspend fun insertAll(items: List<FoodItem>) {
        foodItemDao.insertAll(items)
    }

    /**
     * Consumes [amount] of an item. Hitting zero (or below, from a sloppy tap) finishes the
     * row as USED rather than leaving a phantom 0-quantity item in the list.
     *
     * Every call logs, including the ones that leave food behind: eating two of three eggs
     * is the ordinary case, and it used to vanish because only an emptied row was recorded.
     * The logged amount is clamped to what was actually there, so overshooting the count
     * cannot report more food than the kitchen held.
     *
     * @return the id of the logged event, for undo.
     */
    suspend fun useAmount(item: FoodItem, amount: Double, on: LocalDate = LocalDate.now()): Long =
        transactions.run {
            val consumed = minOf(amount, item.quantity)
            val eventId = record(item, consumed, FinishReason.USED, on)
            val remaining = item.quantity - amount
            if (remaining > 0.0001) {
                foodItemDao.updateQuantity(item.id, remaining)
            } else {
                foodItemDao.markFinished(item.id, on, FinishReason.USED)
            }
            eventId
        }

    /** Snapshots the item into the log. See [ConsumptionEvent] for why it is a snapshot. */
    private suspend fun record(
        item: FoodItem,
        quantity: Double,
        reason: FinishReason,
        on: LocalDate
    ): Long = consumptionEventDao.insert(
        ConsumptionEvent(
            itemId = item.id,
            name = item.name,
            category = item.category,
            storageArea = item.storageArea,
            store = item.store,
            quantity = quantity,
            reason = reason,
            occurredOn = on,
            recordedAt = System.currentTimeMillis(),
            valueCents = shareOfPrice(item.priceCents, item.quantity, quantity)
        )
    )

    /**
     * What [part] of a row costing [priceCents] for [whole] units is worth.
     *
     * Rough by nature — the row's price is the whole receipt line, and buying another one
     * later adds a unit without adding to it. Good enough to answer "roughly how much did
     * we throw away", which is the only question the number is used for.
     */
    private fun shareOfPrice(priceCents: Int?, whole: Double, part: Double): Int? = when {
        priceCents == null -> null
        whole <= 0.0001 -> priceCents
        else -> (priceCents * (part.coerceAtMost(whole) / whole)).roundToInt()
    }

    /** Everything ever logged. Backup export reads through this. */
    suspend fun allEvents(): List<ConsumptionEvent> = consumptionEventDao.getAll()

    suspend fun insertAllEvents(events: List<ConsumptionEvent>) {
        consumptionEventDao.insertAll(events)
    }

    /**
     * Inserts [item], or folds it into an identical row that is already in the same storage
     * area. Buying spinach twice in a week should read as "2 bunches of spinach", not two
     * separate lines that both expire on different days.
     *
     * The surviving row keeps the *earlier* expiry date: the older stock is the one that
     * needs eating first, so warning early is the safe direction to round.
     */
    suspend fun addOrMerge(item: FoodItem): MergeOutcome = transactions.run {
        val existing = foodItemDao.findActiveMatch(item.name, item.storageArea)
            ?: return@run MergeOutcome.Inserted(foodItemDao.insert(item))

        val mergedExpiry = listOfNotNull(existing.expiresOn, item.expiresOn).minOrNull()
        foodItemDao.update(
            existing.copy(
                quantity = existing.quantity + item.quantity,
                expiresOn = mergedExpiry,
                priceCents = sumPrices(existing.priceCents, item.priceCents),
                purchasedOn = maxOf(existing.purchasedOn, item.purchasedOn)
            )
        )
        MergeOutcome.Merged(existing.id)
    }

    private fun sumPrices(a: Int?, b: Int?): Int? =
        if (a == null && b == null) null else (a ?: 0) + (b ?: 0)

    /**
     * Builds a [FoodItem] draft with category and storage area guessed from the name.
     *
     * The expiry date is deliberately left null. A guessed date looks like a fact once it
     * is sitting in the field, and a wrong one either nags about food that is fine or
     * stays quiet about food that is not — so expiry stays empty until someone sets it.
     */
    fun draftFor(
        name: String,
        purchasedOn: LocalDate = LocalDate.now(),
        store: Store = Store.OTHER,
        quantity: Double = 1.0,
        priceCents: Int? = null,
        purchaseId: Long? = null
    ): FoodItem {
        val guess = ShelfLife.guess(name)
        return FoodItem(
            name = name,
            category = guess.category,
            storageArea = guess.storageArea,
            quantity = quantity,
            purchasedOn = purchasedOn,
            expiresOn = null,
            store = store,
            priceCents = priceCents,
            purchaseId = purchaseId
        )
    }

    /**
     * Persists a reviewed receipt: the receipt itself, every parsed line for later
     * debugging, and one [FoodItem] per entry the user kept (merged into an existing row
     * where one matches).
     */
    suspend fun commitReceipt(
        receipt: ParsedReceipt,
        entries: List<ReceiptEntry>,
        imagePath: String? = null
    ): Long = transactions.run {
        val purchaseId = purchaseDao.insert(
            Purchase(
                store = receipt.store,
                purchasedOn = receipt.purchasedOn ?: LocalDate.now(),
                totalCents = receipt.totalCents,
                rawText = receipt.rawText,
                imagePath = imagePath
            )
        )

        // Match on the raw OCR line: the user may have renamed a kept line during review.
        val keptRawLines = entries.map { it.line.rawLine }.toSet()
        purchaseDao.insertLines(
            receipt.lines.map { line ->
                PurchaseLine(
                    purchaseId = purchaseId,
                    rawLine = line.rawLine,
                    parsedName = line.name,
                    quantity = line.quantity,
                    priceCents = line.priceCents,
                    accepted = line.rawLine in keptRawLines
                )
            }
        )

        // One at a time, because each entry may merge into an existing row rather than insert.
        entries.forEach { entry ->
            val draft = draftFor(
                name = entry.name,
                purchasedOn = receipt.purchasedOn ?: LocalDate.now(),
                store = receipt.store,
                quantity = entry.line.quantity,
                priceCents = entry.line.priceCents,
                purchaseId = purchaseId
            )
            addOrMerge(
                if (entry.storageArea != null) {
                    draft.copy(storageArea = entry.storageArea)
                } else {
                    draft
                }
            )
        }
        purchaseId
    }
}
