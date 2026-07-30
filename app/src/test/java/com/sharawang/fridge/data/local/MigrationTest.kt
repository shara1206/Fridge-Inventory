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
}
