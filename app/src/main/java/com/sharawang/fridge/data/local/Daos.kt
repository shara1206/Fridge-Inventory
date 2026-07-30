package com.sharawang.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodItemDao {

    /** Still in the kitchen, soonest to expire first, untracked expiry last. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NULL
        ORDER BY expiresOn IS NULL, expiresOn ASC, name ASC
        """
    )
    fun observeActive(): Flow<List<FoodItem>>

    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NULL AND storageArea = :area
        ORDER BY expiresOn IS NULL, expiresOn ASC, name ASC
        """
    )
    fun observeByArea(area: StorageArea): Flow<List<FoodItem>>

    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NULL AND name LIKE '%' || :query || '%'
        ORDER BY expiresOn IS NULL, expiresOn ASC, name ASC
        """
    )
    fun search(query: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE finishedOn IS NOT NULL ORDER BY finishedOn DESC LIMIT 200")
    fun observeHistory(): Flow<List<FoodItem>>

    /** Feeds the waste report. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NOT NULL AND finishedOn >= :since
        ORDER BY finishedOn DESC
        """
    )
    fun observeFinishedSince(since: LocalDate): Flow<List<FoodItem>>

    /**
     * Case- and whitespace-insensitive lookup used to merge a repeat purchase into the row
     * that is already there. Scoped to one storage area: a bag of frozen peas and a fresh
     * one are genuinely different items.
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NULL
          AND storageArea = :area
          AND LOWER(TRIM(name)) = LOWER(TRIM(:name))
        ORDER BY expiresOn IS NULL, expiresOn ASC
        LIMIT 1
        """
    )
    suspend fun findActiveMatch(name: String, area: StorageArea): FoodItem?

    @Query("UPDATE food_items SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double)

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    /** Still here, has a tracked expiry, and that expiry is on or before [date]. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE finishedOn IS NULL AND expiresOn IS NOT NULL AND expiresOn <= :date
        ORDER BY expiresOn ASC
        """
    )
    suspend fun expiringBy(date: LocalDate): List<FoodItem>

    @Insert
    suspend fun insert(item: FoodItem): Long

    @Insert
    suspend fun insertAll(items: List<FoodItem>): List<Long>

    @Update
    suspend fun update(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)

    @Query("UPDATE food_items SET finishedOn = :on, finishedReason = :reason WHERE id = :id")
    suspend fun markFinished(id: Long, on: LocalDate, reason: FinishReason)

    @Query(
        """
        UPDATE food_items
        SET finishedOn = NULL, finishedReason = NULL, quantity = :quantity
        WHERE id = :id
        """
    )
    suspend fun restore(id: Long, quantity: Double)
}

@Dao
interface PurchaseDao {

    @Insert
    suspend fun insert(purchase: Purchase): Long

    @Insert
    suspend fun insertLines(lines: List<PurchaseLine>): List<Long>

    @Query("SELECT * FROM purchases ORDER BY purchasedOn DESC, id DESC LIMIT 100")
    fun observeRecent(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY id ASC")
    suspend fun linesFor(purchaseId: Long): List<PurchaseLine>
}
