package com.sharawang.fridge.data.repo

import com.sharawang.fridge.data.ShelfLife
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.FoodItemDao
import com.sharawang.fridge.data.local.Purchase
import com.sharawang.fridge.data.local.PurchaseDao
import com.sharawang.fridge.data.local.PurchaseLine
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import com.sharawang.fridge.receipt.ParsedLine
import com.sharawang.fridge.receipt.ParsedReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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

/** Single entry point to the local database. No network, ever. */
class InventoryRepository(
    private val foodItemDao: FoodItemDao,
    private val purchaseDao: PurchaseDao
) {

    fun activeItems(): Flow<List<FoodItem>> = foodItemDao.observeActive()

    fun itemsIn(area: StorageArea): Flow<List<FoodItem>> = foodItemDao.observeByArea(area)

    fun search(query: String): Flow<List<FoodItem>> = foodItemDao.search(query)

    fun history(): Flow<List<FoodItem>> = foodItemDao.observeHistory()

    /** Waste report over the last [days] days. */
    fun wasteReport(days: Long = 30): Flow<WasteReport> =
        foodItemDao.observeFinishedSince(LocalDate.now().minusDays(days))
            .map(WasteReport::from)

    fun recentPurchases(): Flow<List<Purchase>> = purchaseDao.observeRecent()

    suspend fun item(id: Long): FoodItem? = foodItemDao.getById(id)

    /** Everything that expires on or before [date] and has not been used up. */
    suspend fun expiringBy(date: LocalDate): List<FoodItem> = foodItemDao.expiringBy(date)

    /** Manual save. New items go through the merge path; edits update in place. */
    suspend fun save(item: FoodItem): MergeOutcome =
        if (item.id == 0L) {
            addOrMerge(item)
        } else {
            foodItemDao.update(item)
            MergeOutcome.Updated(item.id)
        }

    suspend fun delete(item: FoodItem) = foodItemDao.delete(item)

    suspend fun markFinished(
        id: Long,
        reason: FinishReason,
        on: LocalDate = LocalDate.now()
    ) = foodItemDao.markFinished(id, on, reason)

    /**
     * Undo. [quantity] is restored explicitly because finishing an item can happen after it
     * was partially used, and putting it back with the wrong amount is worse than not
     * offering undo at all.
     */
    suspend fun restore(id: Long, quantity: Double) = foodItemDao.restore(id, quantity)

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

    /** Restores a backup wholesale. Only reached behind an explicit confirmation. */
    suspend fun replaceAll(items: List<FoodItem>) {
        foodItemDao.deleteAll()
        foodItemDao.insertAll(items)
    }

    suspend fun insertAll(items: List<FoodItem>) {
        foodItemDao.insertAll(items)
    }

    /**
     * Consumes [amount] of an item. Hitting zero (or below, from a sloppy tap) finishes the
     * row as USED rather than leaving a phantom 0-quantity item in the list.
     */
    suspend fun useAmount(item: FoodItem, amount: Double) {
        val remaining = item.quantity - amount
        if (remaining > 0.0001) {
            foodItemDao.updateQuantity(item.id, remaining)
        } else {
            foodItemDao.markFinished(item.id, LocalDate.now(), FinishReason.USED)
        }
    }

    /**
     * Inserts [item], or folds it into an identical row that is already in the same storage
     * area. Buying spinach twice in a week should read as "2 bunches of spinach", not two
     * separate lines that both expire on different days.
     *
     * The surviving row keeps the *earlier* expiry date: the older stock is the one that
     * needs eating first, so warning early is the safe direction to round.
     */
    suspend fun addOrMerge(item: FoodItem): MergeOutcome {
        val existing = foodItemDao.findActiveMatch(item.name, item.storageArea)
            ?: return MergeOutcome.Inserted(foodItemDao.insert(item))

        val mergedExpiry = listOfNotNull(existing.expiresOn, item.expiresOn).minOrNull()
        foodItemDao.update(
            existing.copy(
                quantity = existing.quantity + item.quantity,
                expiresOn = mergedExpiry,
                priceCents = sumPrices(existing.priceCents, item.priceCents),
                purchasedOn = maxOf(existing.purchasedOn, item.purchasedOn)
            )
        )
        return MergeOutcome.Merged(existing.id)
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
     * debugging, and one [FoodItem] per line the user kept.
     */
    /**
     * Persists a reviewed receipt: the receipt itself, every parsed line for later
     * debugging, and one [FoodItem] per entry the user kept (merged into an existing row
     * where one matches).
     */
    suspend fun commitReceipt(
        receipt: ParsedReceipt,
        entries: List<ReceiptEntry>,
        imagePath: String? = null
    ): Long {
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
                if (entry.storageArea != null) draft.copy(storageArea = entry.storageArea)
                else draft
            )
        }
        return purchaseId
    }
}
