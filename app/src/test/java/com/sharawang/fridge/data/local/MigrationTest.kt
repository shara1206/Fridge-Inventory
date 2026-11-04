package com.sharawang.fridge.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a version 1 database by hand and runs the real migration over it.
 *
 * Room's MigrationTestHelper needs exported schema JSON, which only exists after a build; this
 * approach has no such dependency, which means the migration is covered from the very first
 * `gradle test` on a clean checkout. Migrations are the one class of bug that destroys the
 * user's data, so they should never be the untested part.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val createV1FoodItems = """
        CREATE TABLE IF NOT EXISTS `food_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `storageArea` TEXT NOT NULL,
            `quantity` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `purchasedOn` TEXT NOT NULL,
            `expiresOn` TEXT,
            `store` TEXT NOT NULL,
            `priceCents` INTEGER,
            `notes` TEXT NOT NULL,
            `finishedOn` TEXT,
            `purchaseId` INTEGER
        )
    """.trimIndent()

    private fun openV1(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(createV1FoodItems)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun insertV1(db: SupportSQLiteDatabase, name: String, category: String) {
        db.execSQL(
            """
            INSERT INTO food_items
                (name, category, storageArea, quantity, unit, purchasedOn, store, notes)
            VALUES ('$name', '$category', 'FRIDGE', 1.0, 'ea', '2026-07-01', 'HMART', '')
            """.trimIndent()
        )
    }

    private fun categories(db: SupportSQLiteDatabase): List<String> {
        val rows = mutableListOf<String>()
        db.query("SELECT category FROM food_items ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) rows.add(cursor.getString(0))
        }
        return rows
    }

    @Test
    fun `old category names are remapped, not dropped`() {
        val db = openV1()
        insertV1(db, "Spinach", "PRODUCE")
        insertV1(db, "Pork Belly", "MEAT_SEAFOOD")
        insertV1(db, "Dumplings", "FROZEN")
        insertV1(db, "Rice", "DRY_GOODS")
        insertV1(db, "Milk", "DAIRY_EGGS")

        MIGRATION_1_2.migrate(db)

        assertEquals(
            listOf("VEGETABLES", "MEAT", "FROZEN_STAPLES", "GRAINS_NOODLES", "DAIRY_EGGS"),
            categories(db)
        )
        db.close()
    }

    @Test
    fun `an unrecognised category becomes OTHER rather than crashing on read`() {
        val db = openV1()
        insertV1(db, "Something odd", "SOME_CATEGORY_THAT_NEVER_EXISTED")

        MIGRATION_1_2.migrate(db)

        assertEquals(listOf("OTHER"), categories(db))
        db.close()
    }

    @Test
    fun `the finishedReason column is added and starts empty`() {
        val db = openV1()
        insertV1(db, "Spinach", "PRODUCE")

        MIGRATION_1_2.migrate(db)

        db.query("SELECT finishedReason FROM food_items").use { cursor ->
            cursor.moveToFirst()
            assertNull(cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun `nothing else about the row changes`() {
        val db = openV1()
        insertV1(db, "Pork Belly", "MEAT_SEAFOOD")

        MIGRATION_1_2.migrate(db)

        db.query("SELECT name, quantity, purchasedOn FROM food_items").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Pork Belly", cursor.getString(0))
            assertEquals(1.0, cursor.getDouble(1), 0.0001)
            assertEquals("2026-07-01", cursor.getString(2))
        }
        db.close()
    }

    // ---- version 2 -> 3: the consumption log --------------------------------

    private val createV2FoodItems = """
        CREATE TABLE IF NOT EXISTS `food_items` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `category` TEXT NOT NULL,
            `storageArea` TEXT NOT NULL,
            `quantity` REAL NOT NULL,
            `unit` TEXT NOT NULL,
            `purchasedOn` TEXT NOT NULL,
            `expiresOn` TEXT,
            `store` TEXT NOT NULL,
            `priceCents` INTEGER,
            `notes` TEXT NOT NULL,
            `finishedOn` TEXT,
            `finishedReason` TEXT,
            `purchaseId` INTEGER
        )
    """.trimIndent()

    private fun openV2(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(createV2FoodItems)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun insertV2(
        db: SupportSQLiteDatabase,
        name: String,
        quantity: Double = 1.0,
        finishedOn: String? = null,
        finishedReason: String? = null,
        priceCents: Int? = null
    ) {
        val on = finishedOn?.let { "'$it'" } ?: "NULL"
        val reason = finishedReason?.let { "'$it'" } ?: "NULL"
        val price = priceCents?.toString() ?: "NULL"
        db.execSQL(
            """
            INSERT INTO food_items
                (name, category, storageArea, quantity, unit, purchasedOn, store, notes,
                 priceCents, finishedOn, finishedReason)
            VALUES ('$name', 'VEGETABLES', 'FRIDGE', $quantity, 'ea', '2026-07-01', 'HMART', '',
                    $price, $on, $reason)
            """.trimIndent()
        )
    }

    private fun events(db: SupportSQLiteDatabase, columns: String): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        db.query("SELECT $columns FROM consumption_events ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                rows.add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }
        return rows
    }

    @Test
    fun `finished rows are carried into the new log so history is not empty after upgrading`() {
        val db = openV2()
        insertV2(
            db,
            "Spinach",
            quantity = 2.0,
            finishedOn = "2026-07-10",
            finishedReason = "DISCARDED",
            priceCents = 499
        )

        MIGRATION_2_3.migrate(db)

        // Quantity is compared as a number: Android's cursor renders REAL 2.0 as "2", so a
        // string comparison would test the cursor's formatting rather than the migration.
        assertEquals(
            listOf(listOf("Spinach", "DISCARDED", "2026-07-10", "499")),
            events(db, "name, reason, occurredOn, valueCents")
        )
        db.query("SELECT quantity FROM consumption_events").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2.0, cursor.getDouble(0), 0.0001)
        }
        db.close()
    }

    @Test
    fun `a finish with no recorded reason stays unknown rather than counting as eaten`() {
        val db = openV2()
        insertV2(db, "Legacy row", finishedOn = "2026-07-10", finishedReason = null)

        MIGRATION_2_3.migrate(db)

        assertNull(events(db, "reason").single().single())
        db.close()
    }

    @Test
    fun `food still in the kitchen is not logged as consumed`() {
        val db = openV2()
        insertV2(db, "Bok Choy")
        insertV2(db, "Milk", finishedOn = "2026-07-10", finishedReason = "USED")

        MIGRATION_2_3.migrate(db)

        assertEquals(listOf(listOf("Milk")), events(db, "name"))
        db.close()
    }

    @Test
    fun `the log survives a fresh v1 database walking the whole chain`() {
        val db = openV1()
        insertV1(db, "Spinach", "PRODUCE")

        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)

        // Nothing was ever finished, so the log is empty rather than absent.
        assertEquals(emptyList<List<String?>>(), events(db, "name"))
        db.close()
    }
}
